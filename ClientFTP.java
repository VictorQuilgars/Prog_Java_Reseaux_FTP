import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.file.Path;
import java.nio.file.Paths;

public class ClientFTP {
    	public static int PORT;
        public static String CLIENT_DIR;

	public static void main(String[] arguments) {
        if (arguments.length < 2) {
            System.err.println("Usage: java ClientFTP <port> <client_dir>");
            System.exit(1);
        }
        PORT = Integer.parseInt(arguments[0]);
		CLIENT_DIR = initClientDir(arguments[1]);


		try {
			System.out.println("Je vais essayer de me connecter...");
			//InetSocketAddress sa = new InetSocketAddress("localhost", PORT);
			//Socket service = new Socket(sa.getAddress(), sa.getPort());
			Socket service = new Socket("localhost", PORT);
			PrintWriter pw = new PrintWriter(new OutputStreamWriter(service.getOutputStream()),true);
			BufferedReader bf = new BufferedReader(new InputStreamReader(service.getInputStream()));
			BufferedReader consoleInput = new BufferedReader(new InputStreamReader(System.in));

			System.out.println("Connexion établie, vous pouvez maintenant envoyer des messages.");
			System.out.println("Tapez 'STOP' pour quitter.");

			String message;
			String response;
			while(true){
				System.out.print("Client : ");
				message = consoleInput.readLine();

				if (message.equalsIgnoreCase("STOP")) {
					System.out.println("Fermeture de la connexion...");
					pw.println("STOP");
					break;
				}

				// Ajout du traitement pour PUT_FILE <filename>
				if (message.startsWith("PUT")) {
					String[] parts = message.split(" ", 2);
					if (parts.length < 2) {
						System.out.println("Nom de fichier manquant.");
						continue;
					}
					String fileName = parts[1];
					Path filePath = Paths.get(CLIENT_DIR, fileName);
					if (!filePath.toFile().exists()) {
						System.out.println("Fichier introuvable : " + filePath);
						continue;
					}

					pw.println(message); // envoie PUT_FILE <filename>
					pw.flush();

					String serverReady = bf.readLine();
					if (!"READY_TO_RECEIVE".equals(serverReady)) {
						System.out.println("Erreur du serveur : " + serverReady);
						continue;
					}

					try (BufferedReader fileReader = new BufferedReader(new java.io.FileReader(filePath.toFile()))) {
						String line;
						while ((line = fileReader.readLine()) != null) {
							pw.println(line);
						}
					}
					pw.println("EOF");
					pw.flush();

					String confirmation = bf.readLine();
					System.out.println("Serveur : " + confirmation);
					continue;
				}

				// Ajout du traitement pour GET_FILE <filename>
				if (message.startsWith("GET")) {
					String[] parts = message.split(" ", 2);
					if (parts.length < 2) {
						System.out.println("Nom de fichier manquant.");
						continue;
					}

					String fileName = parts[1];
					Path outputPath = Paths.get(CLIENT_DIR, fileName);

					pw.println(message);
					pw.flush();

					String serverResponse = bf.readLine();
					if ("FILE_NOT_FOUND".equals(serverResponse)) {
						System.out.println("Serveur : Fichier non trouvé.");
						continue;
					} else if ("READY_TO_SEND".equals(serverResponse)) {
						System.out.println("Serveur : Envoi du fichier en cours...");

						try (BufferedWriter fileWriter = new BufferedWriter(new java.io.FileWriter(outputPath.toFile()))) {
							String line;
							while ((line = bf.readLine()) != null && !line.equals("EOF")) {
								fileWriter.write(line);
								fileWriter.newLine();
							}
						}

						System.out.println("Fichier enregistré dans : " + outputPath.toString());
						continue;
					}
				}

				pw.println(message);
				pw.flush();

				System.out.println("Serveur :");
				while ((response = bf.readLine()) != null && !response.equals("EOF")) {
					if (!response.equals("EOF")) {
						System.out.println("		" + response);						
					}
				}
			}
			
			pw.close();
			bf.close();
			service.close();
			System.out.println("Connexion fermée.");
		} catch (Exception e) {
			System.err.println("Erreur sérieuse : " + e);
			e.printStackTrace();
			System.exit(1);
		}
	}

    private static String initClientDir(String dirName) {
        try {
            Path currentPath = Paths.get(ClientFTP.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            String effectiveName = (dirName != null && !dirName.isBlank()) ? dirName : "Client_Downloads";
            return currentPath.resolve(effectiveName).toString();
        } catch (Exception e) {
            System.err.println("Erreur lors de la détermination du répertoire client : " + e);
            return "./Client_Downloads";
        }
    }
}
