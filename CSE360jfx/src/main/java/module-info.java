// ./src/main/java/module-info.java
module local.jfx360 {
    // Required JavaFX modules
    requires javafx.fxml;
    requires javafx.media;
    requires javafx.web;
    requires javafx.swing;

    // Other required modules based on your dependencies
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires eu.hansolo.toolboxfx;
    requires com.gluonhq.attach.storage;
    requires org.kordamp.ikonli.core;
    requires eu.hansolo.tilesfx;
    requires org.kordamp.bootstrapfx.core;
    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires java.sql;
    requires com.dlsc.formsfx;

    // Exporting packages for other modules to use
    exports local.jfx360.controllers;
//    exports local.jfx360.models;
    // Add other exports if necessary

    // Opening packages to allow JavaFX to perform reflection (necessary for FXML)
    opens local.jfx360.main to javafx.graphics, javafx.fxml;
    exports local.jfx360.utils;
    opens local.jfx360.controllers to javafx.fxml, javafx.graphics;
    opens local.jfx360.utils to javafx.fxml, javafx.graphics;
    // Open other packages as needed for FXML or reflection
}
