import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.*;
import org.w3c.dom.*;
import org.xml.sax.*;

public class JavaProgramm {
    // Farbige Escape-Codes
    private static final String ANSI_RESET = "\u001B[0m";
    private static final String ANSI_CYAN = "\u001B[36m";
    private static final String ANSI_YELLOW = "\u001B[33m";
    private static final String ANSI_RED = "\u001B[31m";
    private static final String ANSI_BLUE = "\u001B[34m";
    private static final String ANSI_GREEN = "\u001B[32m";

    private static int sitemapCounter = 1;

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println(ANSI_YELLOW + "Bitte geben Sie eine Domain als Argument ein." + ANSI_RESET);
            return;
        }

        String domain = args[0];
        if (!isValidDomain(domain)) {
            System.out.println(ANSI_YELLOW + "Ungültige Domain: " + domain + ANSI_RESET);
            return;
        }

        try {
            String protocol = "https";
            String url = protocol + "://" + domain;
            String redirectUrl = followRedirect(url);
            if (redirectUrl != null) {
                System.out.println(ANSI_YELLOW + "Startseite gefunden: " + redirectUrl + ANSI_RESET);
                checkRobotsTxt(redirectUrl, domain);
            } else {
                protocol = "http";
                url = protocol + "://" + domain;
                redirectUrl = followRedirect(url);
                if (redirectUrl != null) {
                    System.out.println(ANSI_YELLOW + "Startseite gefunden: " + redirectUrl + ANSI_RESET);
                    checkRobotsTxt(redirectUrl, domain);
                } else {
                    System.out.println(ANSI_YELLOW + "Keine Startseite gefunden für die Domain: " + domain + ANSI_RESET);
                }
            }
        } catch (URISyntaxException | IOException e) {
            System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten: " + e.getMessage() + ANSI_RESET);
            saveDomainToNotFoundFile(domain);
        }
    }

    private static boolean isValidDomain(String domain) {
        return domain.contains(".");
    }

    private static String followRedirect(String url) throws URISyntaxException, IOException {
        try {
            URI uri = new URI(url);
            URLConnection connection = uri.toURL().openConnection();
            if (connection instanceof HttpURLConnection) {
                HttpURLConnection httpConnection = (HttpURLConnection) connection;
                httpConnection.setInstanceFollowRedirects(false);
                int responseCode = httpConnection.getResponseCode();

                if (responseCode >= 300 && responseCode < 400) {
                    String redirectLocation = httpConnection.getHeaderField("Location");
                    if (redirectLocation != null) {
                        if (!redirectLocation.startsWith("https://") && !redirectLocation.startsWith("http://")) {
                            redirectLocation = url.substring(0, url.indexOf("://") + 3) + redirectLocation;
                        }
                        return redirectLocation;
                    }
                }
            }
        } catch (UnknownHostException e) {
            // Domain konnte nicht aufgelöst werden
            System.out.println(ANSI_RED + "Die Domain konnte nicht aufgelöst werden: " + e.getMessage() + ANSI_RESET);
        }
        return null;
    }

    private static void checkRobotsTxt(String url, String domain) {
        try {
            URI uri = new URI(url);
            URL robotsUrl = uri.resolve("/robots.txt").toURL();
            HttpURLConnection connection = (HttpURLConnection) robotsUrl.openConnection();
            int responseCode = connection.getResponseCode();

            if (responseCode == HttpURLConnection.HTTP_OK) {
                System.out.println(ANSI_BLUE + "Die robots.txt-Datei existiert." + ANSI_RESET);

                InputStream inputStream = connection.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                String line;
                List<String> sitemapUrls = new ArrayList<>();
                boolean sitemapFound = false;
                while ((line = reader.readLine()) != null) {
                    if (line.toLowerCase().startsWith("sitemap:")) {
                        String sitemapUrl = line.substring(8).trim();
                        // Überprüfen, ob die Sitemap-URL zur gleichen Domain gehört
                        if (isSameDomain(url, sitemapUrl)) {
                            sitemapUrls.add(sitemapUrl);
                            System.out.println(line);
                            sitemapFound = true;
                        }
                    }
                }
                reader.close();

                if (!sitemapFound) {
                    System.out.println(ANSI_RED + "Keine Sitemaps in der robots.txt-Datei gefunden." + ANSI_RESET);
                }

                for (String sitemapUrl : sitemapUrls) {
                    checkSitemap(sitemapUrl, domain);
                }
            } else {
                System.out.println(ANSI_YELLOW + "Die robots.txt-Datei existiert nicht." + ANSI_RESET);
            }

            // Wenn keine Sitemaps in der robots.txt gefunden wurden oder die Sitemaps zu einer anderen Domain gehören
            // wird die Standard-Sitemap "/sitemap.xml" verwendet
            checkSitemap(combinePaths(url, "/sitemap.xml"), domain);

        } catch (URISyntaxException | IOException e) {
            System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten: " + e.getMessage() + ANSI_RESET);
            saveDomainToNotFoundFile(domain);
        }
    }

    private static boolean isSameDomain(String baseUrl, String sitemapUrl) {
        try {
            URI baseUri = new URI(baseUrl);
            URI sitemapUri = new URI(sitemapUrl);

            // Vergleichen Sie die Hostnamen beider URLs, um festzustellen, ob sie zur gleichen Domain gehören
            return baseUri.getHost().equalsIgnoreCase(sitemapUri.getHost());
        } catch (URISyntaxException e) {
            // Fehler bei der Überprüfung der URLs, daher wird angenommen, dass sie unterschiedliche Domains sind
            return false;
        }
    }

    private static void checkSitemap(String sitemapUrlString, String domain) {
        String formattedUrl = sitemapUrlString.trim();
        System.out.println(ANSI_CYAN + "Die Sitemap-Datei existiert: " + formattedUrl + ANSI_RESET);

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(URI.create(sitemapUrlString).toURL().openStream());

            NodeList sitemapNodes = document.getElementsByTagName("sitemap");
            if (sitemapNodes.getLength() > 0) {
                System.out.println("Es handelt sich um eine Index-Sitemap.");

                for (int i = 0; i < sitemapNodes.getLength(); i++) {
                    Node sitemapNode = sitemapNodes.item(i);
                    NodeList locNodes = sitemapNode.getChildNodes();
                    for (int j = 0; j < locNodes.getLength(); j++) {
                        Node locNode = locNodes.item(j);
                        if (locNode.getNodeName().equals("loc")) {
                            String sitemapXmlUrl = locNode.getTextContent().trim();
                            System.out.println("XML-Sitemap: " + sitemapXmlUrl);
                            boolean downloaded = downloadSitemap(URI.create(sitemapXmlUrl), domain);
                            if (!downloaded) {
                                // Sitemap konnte nicht heruntergeladen werden, Domain in Datei speichern
                                saveDomainToNotFoundFile(domain);
                            }
                        }
                    }
                }
            } else {
                System.out.println("Es handelt sich um eine normale Sitemap.");
                boolean downloaded = downloadSitemap(URI.create(sitemapUrlString), domain);
                if (!downloaded) {
                    // Sitemap konnte nicht heruntergeladen werden, Domain in Datei speichern
                    saveDomainToNotFoundFile(domain);
                }
            }
        } catch (ParserConfigurationException | SAXException e) {
            System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten: " + e.getMessage() + ANSI_RESET);
            // Domain in Datei speichern
            saveDomainToNotFoundFile(domain);
        } catch (IOException e) {
            String errorMessage = e.getMessage();
            if (errorMessage.contains("Server returned HTTP response code:")) {
                int statusCodeIndex = errorMessage.indexOf(":") + 2;
                String statusCode = errorMessage.substring(statusCodeIndex, statusCodeIndex + 3);
                System.out.println(ANSI_RED + "Server Returned Error mit Code " + statusCode + ANSI_RESET);
            } else {
                System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten: " + errorMessage + ANSI_RESET);
            }
            // Domain in Datei speichern
            saveDomainToNotFoundFile(domain);
        }
    }


    private static boolean downloadSitemap(URI sitemapUri, String domain) {
        try (InputStream in = sitemapUri.toURL().openStream()) {
            String folderName = domain.replace(".", "_");
            File folder = new File(folderName);
            if (!folder.exists()) {
                folder.mkdir();
            }

            String fileName = Paths.get(sitemapUri.getPath()).getFileName().toString();
            int dotIndex = fileName.lastIndexOf(".");
            String filePrefix = fileName.substring(0, dotIndex);
            String fileExtension = fileName.substring(dotIndex + 1);

            String numberedFileName = filePrefix + "_" + sitemapCounter + "." + fileExtension;
            sitemapCounter++;

            File file = new File(folderName + File.separator + numberedFileName);
            Files.copy(in, file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            System.out.println(ANSI_GREEN + "Sitemap wurde heruntergeladen: " + folderName + File.separator + numberedFileName + ANSI_RESET);
            return true; // Sitemap erfolgreich heruntergeladen
        } catch (IOException e) {
            System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten beim Herunterladen der Sitemap: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
            return false; // Sitemap konnte nicht heruntergeladen werden
        }
    }

    private static void saveDomainToNotFoundFile(String domain) {
        try {
            String fileName = "sitemapNotFound.txt";
            Files.write(Paths.get(fileName), (domain + System.lineSeparator()).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            System.out.println(ANSI_YELLOW + "Domain in " + fileName + " gespeichert: " + domain + ANSI_RESET);
        } catch (IOException e) {
            System.out.println(ANSI_RED + "Ein Fehler ist aufgetreten beim Speichern der Domain: " + e.getMessage() + ANSI_RESET);
            e.printStackTrace();
        }
    }

    private static String combinePaths(String url, String path) {
        if (url.endsWith("/") && path.startsWith("/")) {
            return url + path.substring(1);
        } else if (!url.endsWith("/") && !path.startsWith("/")) {
            return url + "/" + path;
        } else {
            return url + path;
        }
    }
}
