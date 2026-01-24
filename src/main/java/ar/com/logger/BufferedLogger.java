package ar.com.logger;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;

public class BufferedLogger {

    private static final String TARGET = System.getProperty("os.name").toLowerCase().contains("win") ? "NUL" : "/dev/null";
    private BufferedWriter writer;

    public BufferedLogger() {
        try {
            this.writer = new BufferedWriter(new FileWriter(TARGET, true), 8192);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void log(String msg) {
        try {
            writer.write("LOG: " + msg + "\n");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    
    public void close() {
        try {
            if (writer != null) writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}