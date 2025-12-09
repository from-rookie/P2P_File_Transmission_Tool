import java.io.*;
import java.util.jar.*;

public class CreateManifest {
    public static void main(String[] args) {
        try {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "client.P2PClient");
            
            FileOutputStream fos = new FileOutputStream("client_manifest.mf");
            manifest.write(fos);
            fos.close();
            
            System.out.println("Client manifest created successfully.");
            
            // Create server manifest
            Manifest serverManifest = new Manifest();
            serverManifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            serverManifest.getMainAttributes().put(Attributes.Name.MAIN_CLASS, "server.P2PServer");
            
            FileOutputStream serverFos = new FileOutputStream("server_manifest.mf");
            serverManifest.write(serverFos);
            serverFos.close();
            
            System.out.println("Server manifest created successfully.");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}