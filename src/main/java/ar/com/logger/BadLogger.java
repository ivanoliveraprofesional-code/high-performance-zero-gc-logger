package ar.com.logger;

import java.io.FileWriter;
import java.io.IOException;

public class BadLogger {
    
    private static final String TARGET = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";

    public void log(String msg) {
        try (FileWriter fw = new FileWriter(TARGET, true)) {
            fw.write("LOG: " + msg + "\n"); 
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}