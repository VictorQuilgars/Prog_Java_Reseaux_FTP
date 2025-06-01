// Removed package declaration to match the expected package structure
import java.io.*;
import java.net.*;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ServerFTP {
	public static int PORT;
    public static String SERVEUR_DIR;
	public static ServerSocket socketAttente = null;

	public static void main(String[] arguments) {
        if (arguments.length < 1) {
			System.err.println("Usage: java ServerFTP <port> <server_dir>");
			System.exit(1);
		}
		PORT = Integer.parseInt(arguments[0]);
		
		// initialisation de répertoire du serveur
		if (arguments.length >= 2) {
			SERVEUR_DIR = initServeurDir(arguments[1]);
		}
		else {
			SERVEUR_DIR = initServeurDir(null);
		}

		try {
			// attente de connexion du client
			socketAttente = new ServerSocket(PORT);
			System.out.println("Attente de connexions, serveur prêt");

			Socket service = socketAttente.accept();
			System.out.println("Client connecté : " + service.getInetAddress());

			// initialisation de flux d'entrée pour la communication avec le client
			BufferedReader bf = new BufferedReader(new InputStreamReader(service.getInputStream()));
			// initialisation du flux de sortie pour la communication avec le client
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(service.getOutputStream()), true);

			String message;
			while ((message = bf.readLine()) != null) {
				System.out.println("Client : " + message);
				
				// vérification si le client veut arrêter la communication
				if (message.equalsIgnoreCase("STOP")) {
					System.out.println("Client a demandé la fermeture de la connexion.");
					pw.println("Connexion fermée.");
					break;
				}

				// Traitement de la commande reçu
				if (message.startsWith("GET")) {
					String[] parts = message.split(" ", 2);
					if (parts.length == 2) {
						handleGetFile(pw, parts[1]);
					} else {
						pw.println("Nom de fichier manquant pour PUT_FILE.");
					}
				} else if (message.startsWith("PUT")) {
					String[] parts = message.split(" ", 2);
					if (parts.length == 2) {
						handlePutFile(pw, bf, parts[1]);
					} else {
						pw.println("Nom de fichier manquant pour PUT_FILE.");
					}
				} else if (message.startsWith("LS")) {
					handleLsDir(pw);
				}
				else {
					pw.println("Requête non reconnue : " + message);
					// EOF pour signaler au client que la réponse est terminée
					pw.println("EOF");
				}
			}

			pw.close();
            bf.close();
            service.close();
            System.out.println("Connexion fermée.");
		} catch (Exception e) {
			System.err.println("Erreur : " + e);
			e.printStackTrace();
			System.exit(1);
		}
		// finally {
		// 	if (socketAttente != null) {
		// 		try {
		// 			socketAttente.close();
		// 		} catch (IOException e) {
		// 			System.err.println("Erreur lors de la fermeture du socket : " + e);
		// 		}
		// 	}
		// }
	}

	private static String initServeurDir(String dirName) {
		try {
			// on détermine le chemin du répertoire du serveur qui sera donc au même niveau que le fichier ServerFTP.class
			Path currentPath = Paths.get(ServerFTP.class.getProtectionDomain().getCodeSource().getLocation().toURI());
			String effectiveName = (dirName != null && !dirName.isBlank()) ? dirName : "Server_Downloads";
			Path fullPath = currentPath.resolve(effectiveName);
	
			// on crée le répertoire s'il n'existe pas
			File directory = fullPath.toFile();
			if (!directory.exists()) {
				if (directory.mkdirs()) {
					System.out.println("Répertoire créé : " + fullPath);
				} else {
					System.err.println("Erreur : impossible de créer le répertoire " + fullPath);
				}
			} else {
				System.out.println("Répertoire existant : " + fullPath);
			}
	
			return fullPath.toString();
		} catch (Exception e) {
			System.err.println("Erreur lors de la détermination du répertoire serveur : " + e);
			return "./Server_Downloads"; // fallback
		}
	}

	private static void handleLsDir(PrintWriter pw) {
		// Vérification de l'existence du répertoire du serveur
		File dir = new File(SERVEUR_DIR);
		File[] files = dir.listFiles();
		// parcours du répertoire et envoi des noms de fichiers et dossiers au client
		if (files != null) {
			pw.println("Contenu du répertoire :");
			for (File file : files) {
				if (file.isFile()) {
					pw.println("FICHIER: " + file.getName());
				} else if (file.isDirectory()) {
					pw.println("DOSSIER: " + file.getName());
				}
			}
			pw.println("EOF"); // marqueur de fin de réponse
			pw.flush();
		} else {
			pw.println("Erreur : Impossible d'accéder au répertoire.");
		}
	}

	private static void handlePutFile(PrintWriter pw, BufferedReader bf, String fileName) {
		try {
			// Vérification de l'existence du répertoire du serveur
			File file = new File(SERVEUR_DIR, fileName);
			if (file.exists()) {
				pw.println("Le fichier existe déjà.");
				return;
			}
			// le serveur a reçu dans un premier temps le nom du fichier à recevoir (à écrire)
			// une fois qu'il a fait ses vérifications, il signale au client qu'il est prêt à recevoir le contenu du fichier
			pw.println("READY_TO_RECEIVE");

			// le serveur va maintenant recevoir le contenu du fichier client ligne par ligne et l'écrire dans le fichier du côté serveur
			try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
				String line;
				while ((line = bf.readLine()) != null) {
					if (line.equals("EOF")) break;
					writer.write(line);
					writer.newLine();
				}
			}
	
			pw.println("Fichier reçu et enregistré sous : " + file.getAbsolutePath());
		} catch (IOException e) {
			pw.println("Erreur lors de la réception du fichier : " + e.getMessage());
		}
	}

	private static void handleGetFile(PrintWriter pw, String fileName) {
		File file = new File(SERVEUR_DIR, fileName);
		if (!file.exists()) {
			pw.println("FILE_NOT_FOUND");
			return;
		}

		pw.println("READY_TO_SEND");
		try (BufferedReader fileReader = new BufferedReader(new java.io.FileReader(file))) {
			String line;
			while ((line = fileReader.readLine()) != null) {
				pw.println(line);
			}
			pw.println("EOF"); // marqueur de fin de fichier
		} catch (IOException e) {
			pw.println("Erreur lors de la lecture du fichier : " + e.getMessage());
		}
	}
}