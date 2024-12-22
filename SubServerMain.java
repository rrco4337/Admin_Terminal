import java.util.Scanner;

public class SubServerMain {

    private SubServerInfo subServerInfo;

    public SubServerMain(SubServerInfo subServerInfo) {
        this.subServerInfo = subServerInfo;
    }

    public void start() {
        // Afficher les informations du sous-serveur
        displaySubServerInfo();

        // Créer un scanner pour lire les entrées du terminal
        Scanner scanner = new Scanner(System.in);
        
        // Interaction avec l'utilisateur pour démarrer le sous-serveur
        while (true) {
            System.out.println("\nEntrez une commande (start, stop, exit) : ");
            String command = scanner.nextLine().toLowerCase().trim();

            switch (command) {
                case "start":
                    startSubServerAction();
                    break;
                case "stop":
                    stopSubServerAction();
                    break;
                case "exit":
                    System.out.println("Arrêt du sous-serveur.");
                    scanner.close();
                    return;
                default:
                    System.out.println("Commande inconnue. Veuillez entrer une commande valide.");
            }
        }
    }

    private void displaySubServerInfo() {
        // Affichage des informations du sous-serveur dans le terminal
        System.out.println("Informations du Sous-Serveur : ");
        System.out.println("Hôte : " + subServerInfo.host);
        System.out.println("Port : " + subServerInfo.port);
        System.out.println("Chemin de stockage : " + subServerInfo.storagePath);
    }

    private void startSubServerAction() {
        // Afficher le message dans le terminal
        System.out.println("Tentative de démarrage du sous-serveur...");

        // Démarre le sous-serveur dans un nouveau thread
        new Thread(() -> Subserver.startSubServer(subServerInfo)).start();
    }

    private void stopSubServerAction() {
        // Logique pour arrêter le sous-serveur
        System.out.println("Arrêt du sous-serveur...");
        Subserver.stopSubServer();
    }

    public static void main(String[] args) {
        // Exemple de création d'un objet SubServerInfo, remplacez cela par vos valeurs réelles
        SubServerInfo subServerInfo = new SubServerInfo("localhost", 8080, "/path/to/storage");

        // Lancer l'application dans le terminal
        SubServerMain subServerMain = new SubServerMain(subServerInfo);
        subServerMain.start();
    }
}
