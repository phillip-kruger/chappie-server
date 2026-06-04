package org.chappiebot.rag;

import io.quarkus.logging.Log;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sql.DataSource;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

/**
 * Downloads and loads pre-generated RAG SQL fragments from the
 * {@code io.quarkus:quarkus-documentation-core-rag} Maven artifact
 * into a pgvector database. The SQL contains vector embeddings for
 * all Quarkus documentation guides.
 */
public class RagSqlLoader {

    private static final String RAG_SQL_PATH = "META-INF/quarkus-rag.sql";
    private static final String RAG_DATA_SQL_PATH = "META-INF/quarkus-rag-data.sql";
    private static final String DEPLOYMENT_SUFFIX = "-deployment";
    private static final String CORE_GROUP_ID = "io.quarkus";
    private static final String AGGREGATED_ARTIFACT_ID = "quarkus-documentation-core-rag";
    private static final String AGGREGATED_GROUP_PATH = "io/quarkus";
    private static final String MAVEN_CENTRAL_BASE = "https://repo1.maven.org/maven2";

    static boolean supportsRagSql(String version) {
        if (version == null || version.isBlank() || version.contains("SNAPSHOT")) {
            return true;
        }
        try {
            String[] parts = version.split("[.\\-]");
            int major = Integer.parseInt(parts[0]);
            int minor = parts.length > 1 ? Integer.parseInt(parts[1]) : 0;
            if (major > 3 || (major == 3 && minor > 36)) {
                return true;
            }
            if (major == 3 && minor == 36) {
                int patch = parts.length > 2 ? Integer.parseInt(parts[2]) : 0;
                return patch >= 1;
            }
            return false;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static final String CREATE_EXTENSION_DDL = "CREATE EXTENSION IF NOT EXISTS vector";
    private static final String CREATE_TABLE_DDL = """
            CREATE TABLE IF NOT EXISTS rag_documents (
                embedding_id UUID PRIMARY KEY,
                embedding vector(384),
                text TEXT,
                metadata JSONB
            )""";
    private static final String CREATE_INDEX_DDL = """
            CREATE INDEX IF NOT EXISTS idx_rag_embedding ON rag_documents
                USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100)""";

    private static final String CORE_SOURCE = "quarkus-documentation";

    public static void ensureLoaded(DataSource dataSource, String quarkusVersion) {
        ensureLoaded(dataSource, quarkusVersion, null);
    }

    public static void ensureLoaded(DataSource dataSource, String quarkusVersion, String projectDir) {
        if (quarkusVersion == null || quarkusVersion.isBlank()) {
            quarkusVersion = detectLatestInstalledVersion();
        }
        if (quarkusVersion == null) {
            Log.warn("Could not determine Quarkus version for RAG loading");
            return;
        }

        if (!supportsRagSql(quarkusVersion)) {
            Log.debugf("Quarkus %s predates RAG SQL support — skipping (using pre-built image)", quarkusVersion);
            return;
        }

        try (Connection conn = dataSource.getConnection()) {
            ensureSchema(conn);
        } catch (SQLException e) {
            Log.warn("Failed to create RAG schema: " + e.getMessage());
            return;
        }

        var loadedSources = queryLoadedSources(dataSource);

        List<String> newSql = new ArrayList<>();

        if (!loadedSources.contains(CORE_SOURCE)) {
            String coreSql = loadCoreSqlContent(quarkusVersion);
            if (coreSql != null) {
                newSql.add(coreSql);
            } else {
                Log.warnf("No core RAG SQL found for Quarkus %s", quarkusVersion);
            }
        }

        for (var fragment : discoverNonCoreFragments(projectDir)) {
            if (!loadedSources.contains(fragment.source)) {
                newSql.add(fragment.sql);
                Log.infof("Found new RAG SQL for extension %s", fragment.source);
            }
        }

        if (newSql.isEmpty()) {
            if (loadedSources.isEmpty()) {
                Log.warn("No RAG SQL fragments found — documentation search will be limited");
            } else {
                Log.debugf("All RAG sources already loaded (%d source(s))", loadedSources.size());
            }
            return;
        }

        executeSql(dataSource, newSql, quarkusVersion);
    }

    private static void ensureSchema(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute(CREATE_EXTENSION_DDL);
            stmt.execute(CREATE_TABLE_DDL);
        }
    }

    private static Set<String> queryLoadedSources(DataSource dataSource) {
        var sources = new HashSet<String>();
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT DISTINCT metadata->>'source' FROM rag_documents")) {
            while (rs.next()) {
                String source = rs.getString(1);
                if (source != null) {
                    sources.add(source);
                }
            }
        } catch (SQLException e) {
            Log.debugf("Failed to query loaded RAG sources: %s", e.getMessage());
        }
        return sources;
    }

    private static String loadCoreSqlContent(String version) {
        Path m2Repo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        Path jarPath = m2Repo.resolve(AGGREGATED_GROUP_PATH)
                .resolve(AGGREGATED_ARTIFACT_ID)
                .resolve(version)
                .resolve(AGGREGATED_ARTIFACT_ID + "-" + version + ".jar");

        String sql = readSqlFromJar(jarPath);
        if (sql != null) {
            Log.infof("Found RAG SQL artifact locally for Quarkus %s", version);
            return sql;
        }

        if (version.endsWith("-SNAPSHOT")) {
            Log.debugf("Skipping remote download for SNAPSHOT version %s", version);
            return null;
        }

        Path downloaded = downloadFromMavenCentral(version, jarPath);
        if (downloaded != null) {
            sql = readSqlFromJar(downloaded);
            if (sql != null) {
                Log.infof("Downloaded RAG SQL artifact for Quarkus %s", version);
                return sql;
            }
        }

        return null;
    }

    private static String readSqlFromJar(Path jarPath) {
        if (!Files.isRegularFile(jarPath)) {
            return null;
        }
        try (JarFile jar = new JarFile(jarPath.toFile())) {
            JarEntry entry = jar.getJarEntry(RAG_DATA_SQL_PATH);
            if (entry == null) {
                entry = jar.getJarEntry(RAG_SQL_PATH);
            }
            if (entry == null) {
                return null;
            }
            try (InputStream is = jar.getInputStream(entry)) {
                return new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (IOException e) {
            Log.debugf("Failed to read RAG SQL from %s: %s", jarPath, e.getMessage());
            return null;
        }
    }

    private static Path downloadFromMavenCentral(String version, Path targetPath) {
        String url = MAVEN_CENTRAL_BASE + "/" + AGGREGATED_GROUP_PATH + "/" + AGGREGATED_ARTIFACT_ID
                + "/" + version + "/" + AGGREGATED_ARTIFACT_ID + "-" + version + ".jar";

        Log.infof("RAG SQL not found locally, downloading from %s...", url);

        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(30))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(60))
                    .GET()
                    .build();

            HttpResponse<InputStream> response = client.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());

            if (response.statusCode() == 200) {
                Files.createDirectories(targetPath.getParent());
                try (InputStream body = response.body()) {
                    Files.copy(body, targetPath, StandardCopyOption.REPLACE_EXISTING);
                }
                Log.infof("Downloaded RAG SQL artifact to %s", targetPath);
                return targetPath;
            } else {
                Log.warnf("RAG SQL artifact not available at %s (HTTP %d)", url, response.statusCode());
                return null;
            }
        } catch (IOException | InterruptedException e) {
            Log.warnf("Failed to download RAG SQL: %s", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private static void executeSql(DataSource dataSource, List<String> sqlFragments, String version) {
        Log.infof("Loading %d RAG SQL fragment(s) for Quarkus %s...", sqlFragments.size(), version);

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);

            try (Statement stmt = conn.createStatement()) {
                stmt.execute(CREATE_EXTENSION_DDL);
                stmt.execute(CREATE_TABLE_DDL);

                for (String sql : sqlFragments) {
                    for (String statement : splitSqlStatements(sql)) {
                        if (!statement.isBlank()) {
                            stmt.execute(statement);
                        }
                    }
                }

                stmt.execute(CREATE_INDEX_DDL);
            }

            conn.commit();

            try (Statement stmt = conn.createStatement();
                    ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM rag_documents")) {
                if (rs.next()) {
                    Log.infof("RAG data loaded: %d documents for Quarkus %s", rs.getLong(1), version);
                }
            }
        } catch (SQLException e) {
            Log.errorf(e, "Failed to load RAG SQL for Quarkus %s", version);
        }
    }

    private record RagFragment(String source, String sql) {
    }

    private static final Pattern SOURCE_PATTERN = Pattern.compile(
            "metadata\\s*->>\\s*'source'\\s*=\\s*'([^']+)'");
    private static final Pattern PROPERTY_PATTERN = Pattern.compile("\\$\\{([^}]+)}");

    private static List<RagFragment> discoverNonCoreFragments(String projectDir) {
        if (projectDir == null || projectDir.isBlank()) {
            return List.of();
        }

        Path pomFile = Path.of(projectDir, "pom.xml");
        if (!Files.isRegularFile(pomFile)) {
            return List.of();
        }

        List<PomDependency> deps = parsePomDependencies(pomFile);
        if (deps.isEmpty()) {
            return List.of();
        }

        Path m2Repo = Path.of(System.getProperty("user.home"), ".m2", "repository");
        List<RagFragment> fragments = new ArrayList<>();

        for (PomDependency dep : deps) {
            if (CORE_GROUP_ID.equals(dep.groupId) || dep.version == null) {
                continue;
            }
            String groupPath = dep.groupId.replace('.', '/');
            Path deploymentJar = m2Repo.resolve(groupPath)
                    .resolve(dep.artifactId + DEPLOYMENT_SUFFIX)
                    .resolve(dep.version)
                    .resolve(dep.artifactId + DEPLOYMENT_SUFFIX + "-" + dep.version + ".jar");

            String sql = readSqlFromJar(deploymentJar);
            if (sql != null) {
                String source = extractSource(sql, dep.artifactId);
                fragments.add(new RagFragment(source, sql));
            }
        }

        return fragments;
    }

    private static String extractSource(String sql, String fallback) {
        Matcher m = SOURCE_PATTERN.matcher(sql);
        return m.find() ? m.group(1) : fallback;
    }

    private record PomDependency(String groupId, String artifactId, String version) {
    }

    private static List<PomDependency> parsePomDependencies(Path pomFile) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            Document doc = factory.newDocumentBuilder().parse(pomFile.toFile());

            Map<String, String> properties = new HashMap<>();
            NodeList propsNodes = doc.getElementsByTagName("properties");
            if (propsNodes.getLength() > 0) {
                Element propsEl = (Element) propsNodes.item(0);
                NodeList children = propsEl.getChildNodes();
                for (int i = 0; i < children.getLength(); i++) {
                    if (children.item(i) instanceof Element el) {
                        properties.put(el.getTagName(), el.getTextContent().trim());
                    }
                }
            }

            List<PomDependency> deps = new ArrayList<>();
            NodeList depNodes = doc.getElementsByTagName("dependency");
            for (int i = 0; i < depNodes.getLength(); i++) {
                Element depEl = (Element) depNodes.item(i);
                if (isInPluginOrManagement(depEl)) {
                    continue;
                }
                String groupId = childText(depEl, "groupId");
                String artifactId = childText(depEl, "artifactId");
                String version = childText(depEl, "version");

                if (groupId == null || artifactId == null) {
                    continue;
                }

                groupId = resolveProperty(groupId, properties);
                artifactId = resolveProperty(artifactId, properties);
                if (version != null) {
                    version = resolveProperty(version, properties);
                    if (version.contains("${")) {
                        version = null;
                    }
                }

                deps.add(new PomDependency(groupId, artifactId, version));
            }
            return deps;
        } catch (Exception e) {
            Log.debugf("Failed to parse pom.xml: %s", e.getMessage());
            return List.of();
        }
    }

    private static String resolveProperty(String value, Map<String, String> properties) {
        if (value == null || !value.contains("${")) {
            return value;
        }
        Matcher m = PROPERTY_PATTERN.matcher(value);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String replacement = properties.getOrDefault(m.group(1), m.group(0));
            m.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static boolean isInPluginOrManagement(Element el) {
        org.w3c.dom.Node parent = el.getParentNode();
        while (parent instanceof Element parentEl) {
            String tag = parentEl.getTagName();
            if ("plugin".equals(tag) || "exclusions".equals(tag) || "dependencyManagement".equals(tag)) {
                return true;
            }
            parent = parentEl.getParentNode();
        }
        return false;
    }

    private static String childText(Element parent, String tagName) {
        NodeList children = parent.getElementsByTagName(tagName);
        if (children.getLength() > 0) {
            String text = children.item(0).getTextContent();
            return text != null ? text.trim() : null;
        }
        return null;
    }

    static List<String> splitSqlStatements(String sql) {
        List<String> statements = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inLineComment = false;

        for (int i = 0; i < sql.length(); i++) {
            char c = sql.charAt(i);

            if (c == '\n') {
                inLineComment = false;
                current.append(c);
                continue;
            }

            if (inLineComment) {
                continue;
            }

            if (c == '-' && i + 1 < sql.length() && sql.charAt(i + 1) == '-' && !inSingleQuote) {
                inLineComment = true;
                continue;
            }

            if (c == '\'') {
                if (inSingleQuote && i + 1 < sql.length() && sql.charAt(i + 1) == '\'') {
                    current.append('\'');
                    current.append('\'');
                    i++;
                    continue;
                }
                inSingleQuote = !inSingleQuote;
            }

            if (c == ';' && !inSingleQuote) {
                String stmt = current.toString().trim();
                if (!stmt.isEmpty()) {
                    statements.add(stmt);
                }
                current = new StringBuilder();
            } else {
                current.append(c);
            }
        }

        String remaining = current.toString().trim();
        if (!remaining.isEmpty()) {
            statements.add(remaining);
        }

        return statements;
    }

    private static String detectLatestInstalledVersion() {
        Path quarkusDir = Path.of(System.getProperty("user.home"), ".m2", "repository", "io", "quarkus", "quarkus-core");
        if (!Files.isDirectory(quarkusDir)) {
            return null;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(quarkusDir, Files::isDirectory)) {
            String latest = null;
            for (Path versionDir : stream) {
                String v = versionDir.getFileName().toString();
                if (!v.contains("SNAPSHOT") && (latest == null || v.compareTo(latest) > 0)) {
                    latest = v;
                }
            }
            return latest;
        } catch (IOException e) {
            return null;
        }
    }
}
