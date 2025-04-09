package cl.camel.routes;

import java.time.LocalTime;
import java.util.List;

import org.apache.camel.builder.RouteBuilder;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import cl.camel.model.Usuario;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ApiRoute extends RouteBuilder {

    @Override
    public void configure() {
    	
    	onException(IllegalArgumentException.class)
        .log("Error al procesar el JSON: ${exception.message}")
        .handled(true)
        .to("file:data/errores?fileName=error_${date:now:yyyyMMddHHmmss}.log"); // Guardar error en archivo
    	
        from("timer:servicio-timer?period=30000") //30seg
            .routeId("Servicio usuario")
            .log("Iniciando ejecución")
            //Según hora seleccionar endpoint
            .process(exchange -> {
                int hour = LocalTime.now().getHour();
                String endpoint = (hour < 12)
                    ? "https://jsonplaceholder.typicode.com/todos"
                    : "https://jsonplaceholder.typicode.com/todos/1";

                log.info("Hora actual: {}h - Endpoint seleccionado: {}", hour, endpoint);
                exchange.setProperty("endpoint", endpoint);
            })

            .toD("${exchangeProperty.endpoint}")
            .log("Respuesta del servicio obtenida")
            
            //Valido JSON
            .process(exchange -> {
                String json = exchange.getIn().getBody(String.class);
                if (json == null || json.trim().isEmpty()) {
                    throw new IllegalArgumentException("El JSON recibido está vacío.");
                }
            })
            .log("JSON validado")
       
            //Guardar JSON en archivo
            .to("file:data/usuarios?fileName=respuesta.json&charset=utf-8")
            .log("Archivo JSON guardado")

            //Convertir JSON a CSV
            .process(exchange -> {
                String json = exchange.getIn().getBody(String.class);
                ObjectMapper mapper = new ObjectMapper();
                StringBuilder csvBuilder = new StringBuilder("id,title,completed\n");

                if (json.trim().startsWith("[")) {
                    List<Usuario> usuarios = mapper.readValue(json, new TypeReference<List<Usuario>>() {});
                    for (Usuario u : usuarios) {
                        csvBuilder.append(u.getId()).append(",\"")
                            .append(u.getTitle().replace("\"", "\"\"")).append("\",")
                            .append(u.isCompleted()).append("\n");
                    }
                } else {
                    Usuario u = mapper.readValue(json, Usuario.class);
                    csvBuilder.append(u.getId()).append(",\"")
                        .append(u.getTitle().replace("\"", "\"\"")).append("\",")
                        .append(u.isCompleted()).append("\n");
                }

                exchange.getIn().setBody(csvBuilder.toString());
            })
            .to("file:data/usuarios?fileName=usuarios.csv&charset=utf-8")
            .log("CSV generado")

            //Leer archivo guardado
            .pollEnrich("file:data/usuarios?fileName=respuesta.json&noop=true")
            .log("Leyendo archivo local...")
            .process(exchange -> {
                String json = exchange.getIn().getBody(String.class);
                ObjectMapper mapper = new ObjectMapper();

                if (json.trim().startsWith("[")) {
                    List<Usuario> usuarios = mapper.readValue(json, new TypeReference<List<Usuario>>() {});
                    log.info("Procesando {} usuarios desde archivo:", usuarios.size());
                    for (Usuario usuario : usuarios) {
                        mostrarUsuario(usuario);
                    }
                } else {
                    Usuario usuario = mapper.readValue(json, Usuario.class);
                    log.info("Procesando un usuario desde archivo:");
                    mostrarUsuario(usuario);
                }
            })

            .log("Proceso completo finalizado.");
    }


    private void mostrarUsuario(Usuario usuario) {
        log.info("ID: {}", usuario.getId());
        log.info("Título: {}", usuario.getTitle());
        log.info("Completado: {}", usuario.isCompleted());
        log.info("────────────────────────────");
    }
}
