package cl.camel.model;

import lombok.Data;

@Data
public class Usuario {
    private int userId;
    private int id;
    private String title;
    private boolean completed;
}

