package cl.camel.model;

import java.io.Serializable;

import lombok.Data;

@Data
public class Token implements Serializable {
	
    /**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private String token;
}

