package com.gesstock.gesstock.migration;

import com.gesstock.gesstock.GesstockApplication;
import jakarta.persistence.Column;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.metamodel.EntityType;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Filet de sécurité des migrations Flyway, sur un VRAI MySQL (H2 ne comprend ni les enum ni la syntaxe MySQL).
 * Désactivé sans configuration, donc sans effet sur {@code mvn test} en local ; la CI le lance avec un service MySQL.
 *
 * <pre>
 *   GESSTOCK_TEST_MYSQL=localhost:3306   GESSTOCK_TEST_MYSQL_USER=root   GESSTOCK_TEST_MYSQL_PASSWORD=
 * </pre>
 * L'utilisateur doit pouvoir créer et supprimer des bases : chaque scénario travaille dans sa propre base jetable.
 */
@EnabledIfEnvironmentVariable(named = "GESSTOCK_TEST_MYSQL", matches = ".+")
class MigrationsMysqlIT {

    // sql_mode strict forcé : le MySQL de WampServer tolère par défaut des valeurs qu'un MySQL standard (Docker, CI) refuse.
    private static final String PARAMS = "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
        + "&sessionVariables=sql_mode='STRICT_ALL_TABLES'";

    private final String hote = System.getenv("GESSTOCK_TEST_MYSQL");
    private final String utilisateur = System.getenv().getOrDefault("GESSTOCK_TEST_MYSQL_USER", "root");
    private final String motDePasse = System.getenv().getOrDefault("GESSTOCK_TEST_MYSQL_PASSWORD", "");
    private final List<String> basesCreees = new ArrayList<>();

    @AfterEach
    void nettoyer() {
        JdbcTemplate admin = jdbc("");
        basesCreees.forEach(base -> admin.execute("DROP DATABASE IF EXISTS `" + base + "`"));
    }

    // ------------------------------------------------------------------ scénarios

    @Test
    void installationNeuve_appliqueToutesLesMigrations_etHibernateValideLeSchema() {
        String base = nouvelleBase();

        try (ConfigurableApplicationContext contexte = demarrer(base, "--spring.flyway.baseline-on-migrate=false")) {
            JdbcTemplate jdbc = contexte.getBean(JdbcTemplate.class);
            assertThat(versionsAppliquees(jdbc)).containsExactly("1", "2", "3", "4", "5", "6", "7");
            assertThat(migrationsEnEchec(jdbc)).isZero();
            assertThat(derives(contexte, base)).as("enum Java absents de la base").isEmpty();
        }
    }

    @Test
    void miseAJourDepuisLaVersion3_fusionneLesRolesConserveLesVentesEtNettoieLAncienModele() {
        String base = nouvelleBase();
        migrerJusqua(base, "3");
        JdbcTemplate jdbc = jdbc(base);
        // état réel d'avant : des comptes aux anciens rôles VENDEUR / USER (dont un cumul), des comptes qui n'ont que
        // l'ancien champ « role » (un rôle connu absent de la table, un rôle inconnu) et une vente de l'ancien modèle
        jdbc.update("INSERT INTO roles (name) VALUES ('ADMIN'), ('VENDEUR'), ('USER')");
        jdbc.update("INSERT INTO utilisateurs (actif, role, username, password) VALUES "
            + "(1, 'ADMIN', 'chef', 'x'), (1, 'VENDEUR', 'vendeur', 'x'), (1, 'USER', 'double', 'x'), (1, 'ROLE_USER', 'ancien', 'x'), "
            + "(1, 'MANAGER', 'oubli', 'x'), (1, 'STAGIAIRE', 'inconnu', 'x')");
        jdbc.update("INSERT INTO utilisateur_roles (role_id, utilisateur_id) SELECT r.id, u.id FROM roles r JOIN utilisateurs u ON "
            + "(r.name = 'ADMIN' AND u.username = 'chef') OR (r.name = 'VENDEUR' AND u.username IN ('vendeur', 'double')) "
            + "OR (r.name = 'USER' AND u.username = 'double')");
        jdbc.update("INSERT INTO article (prix, prix_achat, quantite, seuil_alerte, reference, nom) VALUES (1000, 600, 10, 2, 'A-1', 'Article')");
        Long articleId = jdbc.queryForObject("SELECT id FROM article WHERE reference = 'A-1'", Long.class);
        jdbc.update("INSERT INTO vente (code, quantite, prix_unit, prix_total, montant_total, venteacredit, version, statut, article_id, date_vente) "
            + "VALUES ('VTE-ANCIENNE', 3, 1000, 3000, 3000, 0, 0, 'VALIDEE', ?, NOW())", articleId);

        migrerJusqua(base, null);

        assertThat(jdbc.queryForList("SELECT name FROM roles ORDER BY name", String.class))
            .as("VENDEUR et USER disparaissent, CAISSIER est créé, MANAGER est créé pour le compte qui l'avait en ancien champ")
            .containsExactly("ADMIN", "CAISSIER", "MANAGER");
        assertThat(rolesDe(jdbc, "chef")).isEqualTo("ADMIN");
        assertThat(rolesDe(jdbc, "vendeur")).isEqualTo("CAISSIER");
        assertThat(rolesDe(jdbc, "double")).as("un seul CAISSIER malgré les deux anciens rôles").isEqualTo("CAISSIER");
        assertThat(rolesDe(jdbc, "ancien")).as("compte sans attribution : son ancien champ ROLE_USER devient CAISSIER").isEqualTo("CAISSIER");
        assertThat(rolesDe(jdbc, "oubli")).isEqualTo("MANAGER");
        assertThat(rolesDe(jdbc, "inconnu")).as("rôle inconnu : aucun droit, jamais un rôle inventé").isNull();
        assertThat(colonnesDe(jdbc, base, "utilisateurs")).doesNotContain("role");

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM lignes_vente l JOIN vente v ON v.id = l.vente_id "
            + "WHERE v.code = 'VTE-ANCIENNE' AND l.quantite = 3 AND l.prix_unitaire = 1000 AND l.article_id = ?", Integer.class, articleId))
            .as("l'ancienne vente à un article devient une ligne de vente").isEqualTo(1);
        assertThat(colonnesDe(jdbc, base, "vente")).doesNotContain("article_id", "promotion_id", "quantite", "prix_unit", "prix_total");
        assertThat(tableExiste(jdbc, base, "statistiques_journalieres")).isFalse();
        assertThat(indexDe(jdbc, base, "vente")).contains("idx_vente_statut_date", "idx_vente_session_statut");

        try (ConfigurableApplicationContext contexte = demarrer(base, "--spring.flyway.baseline-on-migrate=false")) {
            assertThat(migrationsEnEchec(contexte.getBean(JdbcTemplate.class))).isZero(); // Hibernate valide le schéma migré
        }
    }

    /**
     * Une base de développement créée par Hibernate (ddl-auto=update) n'a pas d'historique Flyway mais contient déjà
     * les colonnes des migrations récentes. La reprendre « en version 1 » rejoue donc des migrations déjà appliquées et échoue ;
     * il faut la déclarer à la version réelle de son schéma.
     */
    @Test
    void baseDeDeveloppementExistante_baselineEnVersion1EchoueMaisEnVersion3Reussit() {
        String base = nouvelleBase();
        migrerJusqua(base, "3");
        jdbc(base).execute("DROP TABLE flyway_schema_history"); // = base sans historique, comme celle d'Hibernate

        assertThatThrownBy(() -> demarrer(base, "--spring.flyway.baseline-on-migrate=true", "--spring.flyway.baseline-version=1"))
            .as("baseline en version 1 : la migration V3 retente d'ajouter une colonne déjà présente")
            .hasStackTraceContaining("prix_catalogue");

        // la base est restée « en échec » : sans réparation on ne peut rien relancer, d'où l'intérêt de se tromper une seule fois
        jdbc(base).execute("DROP TABLE flyway_schema_history");
        try (ConfigurableApplicationContext contexte = demarrer(base,
            "--spring.flyway.baseline-on-migrate=true", "--spring.flyway.baseline-version=3")) {
            JdbcTemplate jdbc = contexte.getBean(JdbcTemplate.class);
            assertThat(versionsAppliquees(jdbc)).containsExactly("3", "4", "5", "6", "7");
            assertThat(migrationsEnEchec(jdbc)).isZero();
        }
    }

    // ------------------------------------------------------------------ outils

    private String nouvelleBase() {
        String nom = "gesstock_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        jdbc("").execute("CREATE DATABASE `" + nom + "` CHARACTER SET utf8mb4");
        basesCreees.add(nom);
        return nom;
    }

    private String url(String base) {
        return "jdbc:mysql://" + hote + "/" + base + PARAMS;
    }

    private JdbcTemplate jdbc(String base) {
        return new JdbcTemplate(new DriverManagerDataSource(url(base), utilisateur, motDePasse));
    }

    private void migrerJusqua(String base, String version) {
        var configuration = Flyway.configure().dataSource(url(base), utilisateur, motDePasse).locations("classpath:db/migration");
        if (version != null) {
            configuration.target(MigrationVersion.fromVersion(version));
        }
        configuration.load().migrate();
    }

    /** Démarre l'application complète (sans serveur web) contre cette base, comme en production : Flyway puis validate. */
    private ConfigurableApplicationContext demarrer(String base, String... options) {
        List<String> arguments = new ArrayList<>(List.of(
            "--spring.profiles.active=mysql-it",
            "--spring.datasource.url=" + url(base),
            "--spring.datasource.username=" + utilisateur,
            "--spring.datasource.password=" + motDePasse,
            "--spring.datasource.driver-class-name=com.mysql.cj.jdbc.Driver",
            "--spring.flyway.enabled=true",
            "--spring.jpa.hibernate.ddl-auto=validate",
            "--app.cors.allowed-origins=http://localhost"));
        arguments.addAll(Arrays.asList(options));
        return new SpringApplicationBuilder(GesstockApplication.class)
            .web(WebApplicationType.NONE)
            .run(arguments.toArray(String[]::new));
    }

    private List<String> versionsAppliquees(JdbcTemplate jdbc) {
        return jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE version IS NOT NULL ORDER BY installed_rank", String.class);
    }

    private int migrationsEnEchec(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM flyway_schema_history WHERE success = 0", Integer.class);
    }

    private List<String> colonnesDe(JdbcTemplate jdbc, String base, String table) {
        return jdbc.queryForList("SELECT column_name FROM information_schema.columns WHERE table_schema = ? AND table_name = ?",
            String.class, base, table).stream().map(String::toLowerCase).toList();
    }

    private boolean tableExiste(JdbcTemplate jdbc, String base, String table) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = ? AND table_name = ?",
            Integer.class, base, table) > 0;
    }

    private String rolesDe(JdbcTemplate jdbc, String compte) {
        return jdbc.queryForObject("SELECT GROUP_CONCAT(r.name ORDER BY r.name) FROM utilisateur_roles ur "
            + "JOIN utilisateurs u ON u.id = ur.utilisateur_id JOIN roles r ON r.id = ur.role_id WHERE u.username = ?",
            String.class, compte);
    }

    private List<String> indexDe(JdbcTemplate jdbc, String base, String table) {
        return jdbc.queryForList("SELECT DISTINCT index_name FROM information_schema.statistics WHERE table_schema = ? AND table_name = ?",
            String.class, base, table);
    }

    /**
     * Hibernate « validate » vérifie les tables et les colonnes mais PAS les valeurs d'un enum MySQL : ajouter une constante
     * Java sans migration passe la validation, puis échoue à l'insertion (« Data truncated »). Ce contrôle comble ce trou.
     */
    private List<String> derives(ConfigurableApplicationContext contexte, String base) {
        JdbcTemplate jdbc = contexte.getBean(JdbcTemplate.class);
        EntityManagerFactory fabrique = contexte.getBean(EntityManagerFactory.class);
        List<String> problemes = new ArrayList<>();
        for (EntityType<?> entite : fabrique.getMetamodel().getEntities()) {
            Class<?> classe = entite.getJavaType();
            Table table = classe.getAnnotation(Table.class);
            String nomTable = table != null && !table.name().isBlank() ? table.name() : enSnake(classe.getSimpleName());
            for (Field champ : classe.getDeclaredFields()) {
                Enumerated enumere = champ.getAnnotation(Enumerated.class);
                if (!champ.getType().isEnum() || enumere == null || enumere.value() != EnumType.STRING) {
                    continue;
                }
                Column colonne = champ.getAnnotation(Column.class);
                String nomColonne = colonne != null && !colonne.name().isBlank() ? colonne.name() : enSnake(champ.getName());
                List<String> types = jdbc.queryForList("SELECT column_type FROM information_schema.columns "
                    + "WHERE table_schema = ? AND table_name = ? AND column_name = ?", String.class, base, nomTable, nomColonne);
                if (types.isEmpty()) {
                    problemes.add(nomTable + "." + nomColonne + " : colonne introuvable");
                    continue;
                }
                String type = types.get(0);
                if (!type.startsWith("enum(")) {
                    continue; // colonne varchar : aucune liste de valeurs à tenir à jour
                }
                Set<String> valeursBase = Arrays.stream(type.substring(5, type.length() - 1).split(","))
                    .map(valeur -> valeur.replace("'", "").trim()).collect(Collectors.toSet());
                for (Object constante : champ.getType().getEnumConstants()) {
                    if (!valeursBase.contains(((Enum<?>) constante).name())) {
                        problemes.add(nomTable + "." + nomColonne + " : « " + constante + " » absent de " + type);
                    }
                }
            }
        }
        return problemes;
    }

    private static String enSnake(String nom) {
        return nom.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
    }
}
