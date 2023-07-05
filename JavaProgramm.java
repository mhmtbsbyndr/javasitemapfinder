import java.io.*;
import java.net.*;
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

        String protocol = "https";
        String url = protocol + "://" + domain;
        String redirectUrl = followRedirect(url);
        if (redirectUrl != null) {
            System.out.println(ANSI_YELLOW + "Startseite gefunden: " + redirectUrl + ANSI_YELLOW);
            checkRobotsTxt(redirectUrl);
        } else {
            protocol = "http";
            url = protocol + "://" + domain;
            redirectUrl = followRedirect(url);
            if (redirectUrl != null) {
                System.out.println(ANSI_YELLOW + "Startseite gefunden: " + redirectUrl + ANSI_YELLOW);
                checkRobotsTxt(redirectUrl);
            } else {
                System.out.println(ANSI_YELLOW + "Keine Startseite gefunden für die Domain: " + domain + ANSI_RESET);
            }
        }
    }

    private static boolean isValidDomain(String domain) {
        return domain.contains(".");
    }

    private static String followRedirect(String url) {
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
        } catch (URISyntaxException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static void checkRobotsTxt(String url) {
    try {
        URI uri = new URI(url);
        URL robotsUrl = uri.resolve("/robots.txt").toURL();
        HttpURLConnection connection = (HttpURLConnection) robotsUrl.openConnection();
        int responseCode = connection.getResponseCode();

        if (responseCode == HttpURLConnection.HTTP_OK) {
            System.out.println(ANSI_BLUE + "Die robots.txt-Datei existiert." + ANSI_BLUE);

            // Ausgabe der Zeilen mit Sitemap
            InputStream inputStream = connection.getInputStream();
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            List<String> sitemapUrls = new ArrayList<>();
            boolean sitemapFound = false;
            while ((line = reader.readLine()) != null) {
                if (line.toLowerCase().startsWith("sitemap:")) {
                    String sitemapUrl = line.substring(8).trim();
                    sitemapUrls.add(sitemapUrl);
                    System.out.println(line);
                    sitemapFound = true;
                }
            }
            reader.close();

            if (!sitemapFound) {
                System.out.println(ANSI_RED + "Keine Sitemaps in der robots.txt-Datei gefunden." + ANSI_RESET);
                sitemapUrls.add(url + "/sitemap.xml");
            }

            for (String sitemapUrl : sitemapUrls) {
                checkSitemap(sitemapUrl);
            }
        } else {
            System.out.println(ANSI_YELLOW + "Die robots.txt-Datei existiert nicht." + ANSI_RESET);
            checkSitemap(url + "sitemap.xml");
        }
    } catch (URISyntaxException | IOException e) {
        e.printStackTrace();
    }
}

	private static void checkSitemap(String sitemapUrlString) {
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
                    }
                }
            }
        } else {
            System.out.println("Es handelt sich um eine normale Sitemap.");
        }
    } catch (ParserConfigurationException | SAXException | IOException e) {
        e.printStackTrace();
    }
}

}
