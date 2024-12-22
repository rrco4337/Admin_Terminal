import java.io.*;
import java.net.*;
import java.util.*;

public class ServerTransfer {
    private static boolean isRunning = false;
    private static ServerSocket serverSocket;
    private static List<SubServerInfo> subServers = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("Serveur Principal avec Sous-Serveurs");
        
        Scanner scanner = new Scanner(System.in);
        
        // Boucle d'attente des commandes dans le terminal
        while (true) {
            System.out.print("Entrez une commande : ");
            String command = scanner.nextLine().toLowerCase().trim();

            switch (command) {
                case "start":
                    if (!isRunning) {
                        loadConfiguration();
                        new Thread(() -> startServer()).start();
                    } else {
                        System.out.println("Le serveur est déjà en cours d'exécution.");
                    }
                    break;

                case "stop":
                    stopServer();
                    break;

                case "help":
                    printUsage();
                    break;

                case "exit":
                    System.out.println("Arrêt du programme...");
                    scanner.close();
                    return; // Quitter le programme

                default:
                    System.out.println("Commande inconnue : " + command);
                    printUsage();
            }
        }
    }
    
    private static void printUsage() {
        System.out.println("\nCommandes disponibles :");
        System.out.println("  start  - Démarrer le serveur principal.");
        System.out.println("  stop   - Arrêter le serveur principal.");
        System.out.println("  help   - Afficher cette aide.");
    }
    

    private static void startServer() {
        try {
            int port = Integer.parseInt(loadConfig("main.server.port", "1234"));

            if (subServers.isEmpty()) {
                System.out.println("Erreur : Aucun sous-serveur configuré. Vérifiez la configuration.");
                return;
            }

            serverSocket = new ServerSocket(port);
            isRunning = true;
            System.out.println("\nServeur principal démarré sur le port " + port);

            while (isRunning) {
                try {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client connecté : " + clientSocket.getInetAddress());
                    new Thread(() -> handleClient(clientSocket)).start();
                } catch (IOException e) {
                    if (isRunning) {
                        System.out.println("Erreur lors de la connexion du client : " + e.getMessage());
                    }
                }
            }
        } catch (NumberFormatException e) {
            System.out.println("Erreur : Port de serveur principal invalide. Vérifiez la configuration.");
        } catch (IOException e) {
            System.out.println("Erreur lors du démarrage du serveur principal : " + e.getMessage());
        } finally {
            stopServer();
        }
    }

    private static String loadConfig(String key, String defaultValue) {
        Properties config = new Properties();
        try (FileInputStream configFile = new FileInputStream("config.properties")) {
            config.load(configFile);
        } catch (IOException e) {
            System.out.println("Impossible de charger le fichier de configuration. Utilisation des valeurs par défaut.");
        }
        return config.getProperty(key, defaultValue);
    }

    private static void stopServer() {
        try {
            isRunning = false;
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
            System.out.println("Serveur principal arrêté.");
            clearTempFolder();
        } catch (IOException e) {
            System.out.println("Erreur lors de l'arrêt : " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try (InputStream in = clientSocket.getInputStream();
             DataInputStream dataIn = new DataInputStream(in);
             OutputStream out = clientSocket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out)) {

            String command = dataIn.readUTF();

            if ("UPLOAD".equalsIgnoreCase(command)) {
                String fileName = dataIn.readUTF();
                long fileSize = dataIn.readLong();
                System.out.println("Réception du fichier : " + fileName);

                File tempFile = new File("temp/" + fileName);
                tempFile.getParentFile().mkdirs();
                try (FileOutputStream fileOut = new FileOutputStream(tempFile)) {
                    byte[] buffer = new byte[4096];
                    int bytesRead;
                    long remaining = fileSize;
                    while ((bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                        fileOut.write(buffer, 0, bytesRead);
                        remaining -= bytesRead;
                    }
                }

                System.out.println("Fichier temporaire reçu : " + tempFile.getAbsolutePath());
                splitAndDistributeFile(tempFile, subServers.size());
            } else if ("DOWNLOAD".equalsIgnoreCase(command)) {
                String fileName = dataIn.readUTF();
                System.out.println("Client demande le fichier : " + fileName);

                File assembledFile = assembleFileFromSubServers(fileName);
                if (assembledFile != null && assembledFile.exists()) {
                    dataOut.writeUTF("OK");
                    dataOut.writeLong(assembledFile.length());

                    try (FileInputStream fileIn = new FileInputStream(assembledFile)) {
                        byte[] buffer = new byte[4096];
                        int bytesRead;
                        while ((bytesRead = fileIn.read(buffer)) > 0) {
                            dataOut.write(buffer, 0, bytesRead);
                        }
                    }
                    System.out.println("Fichier envoyé au client : " + assembledFile.getName());
                    clearTempFolder();
                } else {
                    dataOut.writeUTF("ERROR");
                    System.out.println("Erreur : fichier non trouvé ou assemblage échoué.");
                }
            } else if ("LIST_FILES".equalsIgnoreCase(command)) {
                String[] files = showConsolidatedFileListAndDownload(subServers);
                if (files != null && files.length > 0) {
                    dataOut.writeUTF("OK");
                    dataOut.writeInt(files.length);
                    for (String file : files) {
                        dataOut.writeUTF(file);
                    }
                } else {
                    dataOut.writeUTF("OK");
                    dataOut.writeInt(0);
                }
            } else if ("DELETE".equalsIgnoreCase(command)) {
                String fileName = dataIn.readUTF();
                System.out.println("Client veut supprimer le fichier : " + fileName);
                List<String> files = deleteFileFromSubServers(fileName);
                if(count(files, "ok")==files.size()) {
                    dataOut.writeUTF("OK");
                    System.out.println("Fichier Supprimé : " + fileName);
                } else if(count(files, "ok")>0) {
                    dataOut.writeUTF("OKE");
                    System.out.println("Fichier Supprimé : " + fileName+"ayant des parties inexistantes");
                } else {
                    dataOut.writeUTF("ERROR");
                    System.out.println("Erreur : fichier non trouvé");
                }
            } else {
                dataOut.writeUTF("UNKNOWN_COMMAND");
            }
        } catch (IOException e) {
            System.out.println("Erreur de transfert : " + e.getMessage());
        }
    }
    private static int count(List<String> files,String test) {
        int retour=0;
        for (String string : files) {
            if(string.equalsIgnoreCase(test)) {
                retour += 1;
            }
        }
        return retour;
    }
    private static void clearTempFolder() {
        File tempDir = new File("temp");
        if (tempDir.exists() && tempDir.isDirectory()) {
            for (File file : tempDir.listFiles()) {
                if (file.isFile() && file.delete()) {
                    System.out.println("Fichier temporaire supprimé : " + file.getName());
                }
            }
        }
    }

    private static void loadConfiguration() {
        Properties properties = new Properties();
        try (InputStream input = new FileInputStream("config.properties")) {
            properties.load(input);

            subServers.clear();
            int index = 1;
            while (true) {
                String hostKey = "subserver." + index + ".host";
                String portKey = "subserver." + index + ".port";
                String pathKey = "subserver." + index + ".storagePath";

                if (!properties.containsKey(hostKey) || !properties.containsKey(portKey) || !properties.containsKey(pathKey)) {
                    break;
                }

                String host = properties.getProperty(hostKey);
                int port = Integer.parseInt(properties.getProperty(portKey));
                String storagePath = properties.getProperty(pathKey);

                subServers.add(new SubServerInfo(host, port, storagePath));
                index++;
            }

            System.out.println("Configuration chargée : " + subServers.size() + " sous-serveurs configurés.");
        } catch (IOException e) {
            System.out.println("Erreur lors du chargement de la configuration : " + e.getMessage());
        }
    }
    private static Map<String, List<String>> collectFilePartsFromServers(List<SubServerInfo> subServers) {
        Map<String, List<String>> fileParts = new HashMap<>();

        for (SubServerInfo server : subServers) {
            try (Socket socket = new Socket(server.host, server.port);
                 OutputStream out = socket.getOutputStream();
                 DataOutputStream dataOut = new DataOutputStream(out);
                 InputStream in = socket.getInputStream();
                 DataInputStream dataIn = new DataInputStream(in)) {

                dataOut.writeUTF("LIST_PARTS");

                String response = dataIn.readUTF();
                if ("OK".equalsIgnoreCase(response)) {
                    int partCount = dataIn.readInt();
                    for (int i = 0; i < partCount; i++) {
                        String partName = dataIn.readUTF();

                        // Extraire le nom de fichier sans extension ".partX"
                        String fileName = partName.substring(0, partName.lastIndexOf(".part"));
                        fileParts.putIfAbsent(fileName, new ArrayList<>());
                        fileParts.get(fileName).add(partName);
                    }
                }
            } catch (IOException e) {
                System.out.println("Erreur de connexion au sous-serveur " + server + " : " + e.getMessage() + "\n");
            }
        }
        return fileParts;
    }
    private static String[] showConsolidatedFileListAndDownload(List<SubServerInfo> subServers) {
        Map<String, List<String>> fileParts = collectFilePartsFromServers(subServers);

        // Construire une liste des fichiers complets disponibles
        String[] consolidatedFiles = fileParts.keySet().toArray(new String[0]);
        return consolidatedFiles;
    }

    private static void splitAndDistributeFile(File file, int numParts) throws IOException {
        // Taille de chaque partie
        long fileSize = file.length();
        long partSize = fileSize / numParts;
        long remainingBytes = fileSize % numParts;

        try (FileInputStream fileIn = new FileInputStream(file)) {
            for (int i = 0; i < numParts; i++) {
                File partFile = new File("temp/" + file.getName() + ".part" + (i + 1));
                try (FileOutputStream partOut = new FileOutputStream(partFile)) {
                    byte[] buffer = new byte[4096];
                    long bytesToWrite = partSize + (i == numParts - 1 ? remainingBytes : 0);
                    int bytesRead;
                    while (bytesToWrite > 0 && (bytesRead = fileIn.read(buffer, 0, (int) Math.min(buffer.length, bytesToWrite))) > 0) {
                        partOut.write(buffer, 0, bytesRead);
                        bytesToWrite -= bytesRead;
                    }
                }

                // Envoyer la partie au sous-serveur
                distributePartToSubServer(partFile, subServers.get(i));

                if (partFile.delete()) {
                    System.out.println("Partie supprimée : " + partFile.getName() + "\n");
                } else {
                    System.out.println("Impossible de supprimer : " + partFile.getName() + "\n");
                }
            }
        }
    }

    private static void distributePartToSubServer(File partFile, SubServerInfo subServer) {
        try (Socket socket = new Socket(subServer.host, subServer.port);
             OutputStream out = socket.getOutputStream();
             DataOutputStream dataOut = new DataOutputStream(out);
             FileInputStream fileIn = new FileInputStream(partFile)) {

            // Envoyer la commande UPLOAD au sous-serveur
            dataOut.writeUTF("UPLOAD");
            dataOut.writeUTF(partFile.getName());
            dataOut.writeLong(partFile.length());

            // Transférer la partie
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fileIn.read(buffer)) > 0) {
                dataOut.write(buffer, 0, bytesRead);
            }

            System.out.println("Partie envoyée : " + partFile.getName() + " -> " + subServer + "\n");

        } catch (IOException e) {
            System.out.println("Erreur lors de la distribution vers " + subServer + ": " + e.getMessage() + "\n");
        }
    }

    private static File assembleFileFromSubServers(String fileName) {
        File assembledFile = new File("temp/" + fileName);
    
        try (FileOutputStream fileOut = new FileOutputStream(assembledFile)) {
            for (int i = 0; i < subServers.size(); i++) {
                SubServerInfo subServer = subServers.get(i);
                String partName = fileName + ".part" + (i + 1);
    
                try (Socket socket = new Socket(subServer.host, subServer.port);
                     OutputStream out = socket.getOutputStream();
                     DataOutputStream dataOut = new DataOutputStream(out);
                     InputStream in = socket.getInputStream();
                     DataInputStream dataIn = new DataInputStream(in)) {
    
                    // Demande de la partie au sous-serveur
                    dataOut.writeUTF("SEND_PART");
                    dataOut.writeUTF(partName);
    
                    String response = dataIn.readUTF();
                    if ("OK".equalsIgnoreCase(response)) {
                        long partSize = dataIn.readLong();
    
                        byte[] buffer = new byte[4096];
                        long remaining = partSize;
                        int bytesRead;
                        while (remaining > 0 && (bytesRead = dataIn.read(buffer, 0, (int) Math.min(buffer.length, remaining))) > 0) {
                            fileOut.write(buffer, 0, bytesRead);
                            remaining -= bytesRead;
                        }
                        System.out.println("Partie récupérée et assemblée : " + partName + "\n");
                    } else {
                        System.out.println("Erreur : partie non trouvée sur " + subServer + "\n");
                        return null;
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de l'assemblage du fichier : " + e.getMessage() + "\n");
            return null;
        }

        assembledFile.deleteOnExit();
        return assembledFile;
    }

    private static List<String> deleteFileFromSubServers(String fileName) {
        List<String> retour = new ArrayList<>();
        try {
            for (int i = 0; i < subServers.size(); i++) {
                SubServerInfo subServer = subServers.get(i);
                String partName = fileName + ".part" + (i + 1);
    
                try (Socket socket = new Socket(subServer.host, subServer.port);
                     OutputStream out = socket.getOutputStream();
                     DataOutputStream dataOut = new DataOutputStream(out);
                     InputStream in = socket.getInputStream();
                     DataInputStream dataIn = new DataInputStream(in)) {
    
                    // Demande de la partie au sous-serveur
                    dataOut.writeUTF("DELETE_PART");
                    dataOut.writeUTF(partName);
    
                    String response = dataIn.readUTF();
                    if ("OK".equalsIgnoreCase(response)) {
                        System.out.println("Partie supprimée : " + partName + "\n");
                        retour.add("ok");
                    } else {
                        System.out.println("Erreur : partie non trouvée sur " + subServer + "\n");
                        retour.add("non");
                    }
                }
            }
        } catch (IOException e) {
            System.out.println("Erreur lors de la suppression du fichier : " + e.getMessage() + "\n");
        }
        return retour;
    }
}
