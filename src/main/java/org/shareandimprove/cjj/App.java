package org.shareandimprove.cjj;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;

/**
 * The main application class that sets up and starts the HTTP server.
 * It configures all the necessary context handlers and filters for the application endpoints.
 */
public class App {
    private static final int DEFAULT_PORT = 8888;
    private static final String FILE_ROOT_DIR = "./files";
    private static final String STATIC_DIR = "./static";

    /**
     * The main entry point of the application.
     * It initializes and starts the HttpServer, binding handlers to their respective paths.
     *
     * @param args Command line arguments. Can use -p to specify port number.
     * @throws IOException if the server cannot be started.
     */
    public static void main(String[] args) throws IOException{
        // Load configuration first
        try {
            Config.load("config.json");
            Config.startHotReload("config.json");
        } catch (IOException e) {
            System.err.println("Failed to load config.json: " + e.getMessage());
            System.exit(1);
        }

        int port = DEFAULT_PORT;
        
        // Parse command line arguments for port number
        for (int i = 0; i < args.length; i++) {
            if ("-p".equals(args[i]) && i + 1 < args.length) {
                try {
                    port = Integer.parseInt(args[i + 1]);
                    break;
                } catch (NumberFormatException e) {
                    System.err.println("Invalid port number provided, using default: " + DEFAULT_PORT);
                    port = DEFAULT_PORT;
                }
            }
        }

        File fileRootFile = new File(FILE_ROOT_DIR);
        if (!fileRootFile.exists()) {
            System.out.println("Directory " + FILE_ROOT_DIR + " does not exist. Creating...");
            if (!fileRootFile.mkdirs()) {
                System.err.println("Failed to create directory: " + FILE_ROOT_DIR);
            }
        }

        File staticFile = new File(STATIC_DIR);
        if (!staticFile.exists()) {
             System.out.println("Directory " + STATIC_DIR + " does not exist. Creating...");
            if (!staticFile.mkdirs()) {
                System.err.println("Failed to create directory: " + STATIC_DIR);
            }
        }
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        DownloadHandler downloadHandler = new DownloadHandler(FILE_ROOT_DIR);

        // 1. /login for authentication
        server.createContext("/login", new LoginHandler(STATIC_DIR));
        // 2. /api/search for POST queries
        server.createContext("/api/search", new SearchAPIHandler()).getFilters().add(new AuthFilter());
        // 5. /api/stats (New)
        server.createContext("/api/stats", new StatsAPIHandler()).getFilters().add(new AuthFilter());
        // 3. /api/download and /download/ for file downloading
        server.createContext("/api/download", downloadHandler).getFilters().add(new AuthFilter());
        server.createContext("/download/", downloadHandler).getFilters().add(new AuthFilter());
        // 4. / to serve ./static/ (should be last to not override other contexts)
        server.createContext("/", new StaticFileHandler(STATIC_DIR));

        server.start();
        System.out.println("Server started on port " + port);
    }
}
