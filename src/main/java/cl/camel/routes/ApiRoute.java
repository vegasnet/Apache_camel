package cl.camel.routes;

import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.model.dataformat.JsonLibrary;
import org.springframework.stereotype.Component;

@Component
public class ApiRoute extends RouteBuilder {

    @Override
    public void configure() throws Exception {

        // Manejo de excepciones
        onException(IllegalArgumentException.class)
            .log("Error al procesar el JSON: ${exception.message}")
            .handled(true)
            .to("file:data/errores?fileName=error_${date:now:yyyyMMddHHmmss}.log");

        // Ruta que Inicial
        from("timer:disparador?repeatCount=1")
            .routeId("RutaInicial")
            .log("Ejecutado a las ${date:now:yyyy-MM-dd HH:mm:ss}")
            .choice()
            .when(simple("${properties:token} == 'true'"))
                .to("direct:token")
            .otherwise()
            	.to("direct:token2")
            .end();
           

        // Ruta principal del servicio
        from("direct:token")
            .routeId("RutaHabilitada")
            .log("Iniciando ejecución de ServicioToken")

            // Llamada HTTP al servicio externo
            .choice()
                .when(simple("${properties:token} == 'true'"))
                    .log("Request a endpoint ...")
                    //.toD("http://localhost:8080/api/arca/wssa/${header.dynamicFolder}");
                    .to("http://localhost:8080/api/arca/wssa")
                    .log("Respuesta del servicio obtenida: ${body}")
                .otherwise()
                    .log("No hay Endpoint configurado en propertie (token)")
            .end()

            // Validar contenido
            .convertBodyTo(String.class)
            .filter().simple("${body} == null || ${body.trim().isEmpty()}")
                .throwException(new IllegalArgumentException("El JSON recibido está vacío."))
            .end()

            // Guardar JSON 
            .to("file:data/token?fileName=token.json&charset=utf-8")
            .log("Archivo JSON guardado en data/token/token.json")

            // Guardar TXT
            .to("file:data/token?fileName=token.txt&charset=utf-8")
            .log("Archivo TXT guardado en data/token/token.txt")

            // Convertir JSON a CSV
            .unmarshal().json() 
            .marshal().csv()
            .to("file:data/token?fileName=token.csv&charset=utf-8")
            .log("CSV generado correctamente en data/token/token.csv")

            // Leer archivo JSON guardado
            .pollEnrich("file:data/token?fileName=token.json&noop=true")
            .log("Leyendo archivo JSON local...")
            .unmarshal().json(JsonLibrary.Jackson)
            .choice()
                .when(simple("${body} is 'java.util.List'"))
                    .split().body()
                    .log("Token: ${body[token]}")
                .endChoice()
                .otherwise()
                    .log("Token: ${body[token]}")
            .end()

            // Leer y mostrar archivo TXT
            .pollEnrich("file:data/token?fileName=token.txt&noop=true")
            .convertBodyTo(String.class)
            .log("Contenido del archivo TXT")
            .log("${body}")
            
            .log("Proceso finalizado.")
            .log("────────────────────────────────────────");
        
    // Ruta desactivada
        from("direct:token2")
        	.routeId("RutaDesactivada")
        	.log("Ruta desactivada");
        
        
     // Ruta de busqueda y mover archivo
        from("file:data/token?fileName=token.json&noop=true")
            .routeId("RutaProcesarArchivos")
            .log("Leyendo archivo: ${file:name}")
            .convertBodyTo(String.class) 
            .filter().simple("${body} contains 'version'") 
                .to("file:data/error?fileName=error_${file:name}") // Mover archivo con error a otra carpeta
                .log("Archivo con error guardado: ${file:name}")
            .end()
            .to("file:data/processed?fileName=${file:name}") // Mover archivo procesado a la carpeta 'processed'
            .log("Archivo procesado guardado: ${file:name}");
        
        
// -----------------------------------------EJEMPLOS-------------------------------------------------------------------------------------------------       
//         Ruta cada cierto tiempo disparar ruta
//        from("timer:reloj?period=60000") // 60 segundos
//            .routeId("RutaTimer")
//            .to("direct:token");


//         Ruta para consultar una base de datos o puedo utilizar un .bean llamando a un metodo en de usar .process
//        from("timer:consultaTransacciones?repeatCount=1")
//            .routeId("ConsultaTransacciones")
//            .setBody(constant("cliente123")) // Nombre del cliente a consultar
//            .log("Contando transacciones del cliente: ${body}")
//            .process(exchange -> {
//                String customer = exchange.getIn().getBody(String.class);
//                long count = repository.countByCustomer(customer);
//                exchange.getMessage().setBody("Cliente: " + customer + " → Total transacciones: " + count);
//            })
//            .log("${body}")
//            .log("Consulta de transacciones finalizada.");
        
    }
}
