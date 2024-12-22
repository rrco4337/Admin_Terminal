import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class Subserver {

    private static boolean isRunning = true;
    private static ServerSocket subServerSocket;
    public static void startSubServer(SubServerInfo subServer) {
        try {
            subServerSocket = new ServerSocket(subServer.port);
            System.out.println("Sous-serveur démarré : " + subServer);

            File storageDir = new File(subServer.storagePath);
            if (!storageDir.exists()) {
                storageDir.mkdirs();
            }

            handleSubServer(subServer, subServerSocket);
        } catch (IOException e) {
            System.out.println("Erreur au démarrage du sous-serveur " + subServer + ": " + e.getMessage());
        } finally {
            stopSubServer();
        }
    }

    public static void stopSubServer() {
        try {
            isRunning = false;
            if (subServerSocket != null && !subServerSocket.isClosed()) {
                subServerSocket.close();
            }
            System.out.println(" Sous serveur arrêté.");
        } catch (IOException e) {
            System.out.println("Erreur lors de l'arrêt : " + e.getMessage());
        }
    }

    private static void handleSubServer(SubServerInfo subServer, ServerSocket subServerSocket) {
        while (isRunning) {
            try (Socket clientSocket = subServerSocket.accept();
                 InputStream in = clientSocket.getInputStream();
                 DataInputStream dataIn = new DataInputStream(in);
                 OutputStream out = clientSocket.getOutputStream();
                 DataOutputStream dataOut = new DataOutputStream(out)) {

                String command = dataIn.readUTF();

                if ("UPLOAD".equalsIgnoreCase(command)) {
                    String fileName = dataIn.readUTF();
                    long fileSize = dataIn.readLong();

                    File receivedFile = new File(subServer.storagePath, fileName);
                    try (FileOutputStream fileOut = new FileOutputStream(receivedFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        long remaining = fileSize;
                        while ((bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                            fileOut.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;
                        }
                    }

                    System.out.println("Fichier reçu par " + subServer + ": " + fileName);
                } else if ("SEND_PART".equalsIgnoreCase(command)) {
                    String partName = dataIn.readUTF();

                    File partFile = new File(subServer.storagePath, partName);
                    if (partFile.exists()) {
                        dataOut.writeUTF("OK");
                        dataOut.writeLong(partFile.length());

                        try (FileInputStream fileIn = new FileInputStream(partFile)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = fileIn.read(buffer)) > 0) {
                                dataOut.write(buffer, 0, bytesRead);
                            }
                        }
                        System.out.println("Partie envoyée : " + partFile.getName());
                    } else {
                        dataOut.writeUTF("ERROR");
                        System.out.println("Partie introuvable : " + partName);
                    }
                } else if ("DELETE_PART".equalsIgnoreCase(command)) {
                    String partName = dataIn.readUTF();

                    File partFile = new File(subServer.storagePath, partName);
                    if (partFile.exists()) {
                        dataOut.writeUTF("OK");
                        partFile.delete();
                        System.out.println("Partie supprimée : " + partFile.getName());
                    } else {
                        dataOut.writeUTF("ERROR");
                        System.out.println("Partie introuvable : " + partName);
                    }
                } else if (command.equalsIgnoreCase("LIST_PARTS")) {
                    File directory = new File(subServer.storagePath); // Répertoire des fichiers
                    File[] files = directory.listFiles();
                    if (files != null) {
                        dataOut.writeUTF("OK");
                        dataOut.writeInt(files.length); // Nombre de fichiers
                        for (File file : files) {
                            if (file.isFile() && file.getName().contains(".part")) {
                                dataOut.writeUTF(file.getName());
                            }
                        }
                    } else {
                        dataOut.writeUTF("ERROR");
                    }
                }                
            } catch (IOException e) {
                System.out.println("Erreur dans " + subServer + ": " + e.getMessage());
            }
        }
    }
}
