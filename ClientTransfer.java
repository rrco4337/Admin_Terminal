import java.io.*;
import java.net.*;
import java.util.Scanner;

public class ClientTransfer {
    private static File selectedFile;

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);

        System.out.println("=== Client de Transfert de Fichiers ===");

        System.out.print("IP du serveur (par défaut: localhost) : ");
        String serverIP = scanner.nextLine().trim();
        if (serverIP.isEmpty()) serverIP = "localhost";

        int port = 1234;
        while (true) {
            System.out.print("Port du serveur (par défaut: 1234) : ");
            String portInput = scanner.nextLine().trim();
            try {
                port = portInput.isEmpty() ? 1234 : Integer.parseInt(portInput);
                break;
            } catch (NumberFormatException e) {
                System.out.println("Port invalide. Veuillez entrer un nombre entier.");
            }
        }

        while (true) {
            System.out.print("Entrez une commande : ");

            String command = scanner.nextLine().trim();

            if (command.equalsIgnoreCase("ls")) {
                showFileList(serverIP, port);
            } else if (command.startsWith("upload ")) {
                String filePath = command.substring(7).trim();
                selectedFile = new File(filePath);
                if (selectedFile.exists() && selectedFile.isFile()) {
                    sendFile(serverIP, port);
                } else {
                    System.out.println("Fichier invalide ou introuvable.");
                }
            } else if (command.startsWith("download ")) {
                String fileName = command.substring(9).trim();
                downloadFile(fileName, serverIP, port);
            } else if (command.startsWith("delete ")) {
                String fileName = command.substring(7).trim();
                deleteFile(fileName, serverIP, port);
            } else if (command.equalsIgnoreCase("exit")) {
                System.out.println("Fermeture du client.");
                scanner.close();
                return;
            } else if (command.equalsIgnoreCase("help")) {
                System.out.println("- ls : Afficher les fichiers disponibles sur le serveur");
                System.out.println("- upload /chemindufichier : Envoyer un fichier au serveur");
                System.out.println("- download /nomdufichier : Télécharger un fichier depuis le serveur");
                System.out.println("- exit : Quitter le programme");
            } else {
                System.out.println("Commande invalide, veuillez réessayer. Pour plus d'information sur les commandes utiliser help");
            }
        }
    }

    private static void sendFile(String serverIP, int port) {
        if (selectedFile == null) {
            System.out.println("Aucun fichier sélectionné.");
            return;
        }

        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             FileInputStream fileIn = new FileInputStream(selectedFile)) {

            dataOut.writeUTF("UPLOAD");
            dataOut.writeUTF(selectedFile.getName());
            dataOut.writeLong(selectedFile.length());

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }

            System.out.println("Fichier envoyé avec succès : " + selectedFile.getName());
        } catch (IOException e) {
            System.out.println("Erreur d'envoi : " + e.getMessage());
        }
    }

    private static void showFileList(String serverIP, int port) {
        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             InputStream in = socket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in)) {

            dataOut.writeUTF("LIST_FILES");

            String response = dataIn.readUTF();
            if ("OK".equalsIgnoreCase(response)) {
                int fileCount = dataIn.readInt();
                if (fileCount == 0) {
                    System.out.println("Aucun fichier disponible sur le serveur.");
                    return;
                }

                System.out.println("Fichiers disponibles :");
                for (int i = 0; i < fileCount; i++) {
                    System.out.println("- " + dataIn.readUTF());
                }
            } else {
                System.out.println("Erreur : le serveur n'a pas pu fournir la liste des fichiers.");
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la connexion au serveur : " + e.getMessage());
        }
    }

    private static void downloadFile(String fileName, String serverIP, int port) {
        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             InputStream in = socket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in)) {

            dataOut.writeUTF("DOWNLOAD");
            dataOut.writeUTF(fileName);

            String response = dataIn.readUTF();
            if ("OK".equalsIgnoreCase(response)) {
                long fileSize = dataIn.readLong();

                File tempFile = new File("telechargement/Téléchargé_" + fileName);
                tempFile.getParentFile().mkdirs();
                try (FileOutputStream fileOut = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    long remaining = fileSize;
                    int bytesRead;
                    while (remaining > 0 && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }
                System.out.println("Fichier téléchargé avec succès : " + fileName);
            } else {
                System.out.println("Erreur : fichier non trouvé sur le serveur ou assemblage echoue.");
            }
        } catch (IOException e) {
            System.out.println("Erreur de téléchargement : " + e.getMessage());
        }
    }

    private static void deleteFile(String fileName, String serverIP, int port) {
        try (Socket socket = new Socket(serverIP, port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             InputStream in = socket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in)) {

            dataOut.writeUTF("DELETE");
            dataOut.writeUTF(fileName);

            String response = dataIn.readUTF();
            if ("OK".equalsIgnoreCase(response)) {
                System.out.println("Fichier supprimé avec succès : " + fileName);
            } else if("OKE".equalsIgnoreCase(response)){
                System.out.println("Parties existantes de : " + fileName+" supprimées");
            }  else {
                System.out.println("Erreur : fichier non trouvé sur le serveur.");
            }
        } catch (IOException e) {
            System.out.println("Erreur de téléchargement : " + e.getMessage());
        }
    }
}
