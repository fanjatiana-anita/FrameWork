package utiles;

import jakarta.servlet.ServletContext;
import java.io.InputStream;
import java.util.Properties;

/**
 * Gestionnaire de configuration pour l'authentification et l'autorisation.
 * Lit le fichier auth.conf depuis WEB-INF/
 */
public class AuthConfig {
    
    private static final String CONFIG_FILE = "/WEB-INF/auth.conf";
    private static final String DEFAULT_AUTH_KEY = "loggedIn";
    private static final String DEFAULT_ROLE_KEY = "role";
    private static final String DEFAULT_LOGIN_PAGE = "/login";
    private static final String DEFAULT_FORBIDDEN_PAGE = "/forbidden";
    
    private String authSessionKey;
    private String roleSessionKey;
    private String loginPage;
    private String forbiddenPage;
    
    private static AuthConfig instance;
    
    private AuthConfig() {
        // Valeurs par défaut
        this.authSessionKey = DEFAULT_AUTH_KEY;
        this.roleSessionKey = DEFAULT_ROLE_KEY;
        this.loginPage = DEFAULT_LOGIN_PAGE;
        this.forbiddenPage = DEFAULT_FORBIDDEN_PAGE;
    }
    
    /**
     * Charge la configuration depuis le fichier auth.conf
     */
    public static synchronized void loadConfig(ServletContext context) {
        if (instance == null) {
            instance = new AuthConfig();
        }
        
        try (InputStream is = context.getResourceAsStream(CONFIG_FILE)) {
            if (is == null) {
                System.out.println("⚠️ Fichier " + CONFIG_FILE + " non trouvé. Utilisation des valeurs par défaut.");
                System.out.println("   auth.session.key = " + instance.authSessionKey);
                System.out.println("   auth.role.key = " + instance.roleSessionKey);
                return;
            }
            
            Properties props = new Properties();
            props.load(is);
            
            instance.authSessionKey = props.getProperty("auth.session.key", DEFAULT_AUTH_KEY);
            instance.roleSessionKey = props.getProperty("auth.role.key", DEFAULT_ROLE_KEY);
            instance.loginPage = props.getProperty("auth.login.page", DEFAULT_LOGIN_PAGE);
            instance.forbiddenPage = props.getProperty("auth.forbidden.page", DEFAULT_FORBIDDEN_PAGE);
            
            System.out.println("✅ Configuration d'authentification chargée :");
            System.out.println("   auth.session.key = " + instance.authSessionKey);
            System.out.println("   auth.role.key = " + instance.roleSessionKey);
            System.out.println("   auth.login.page = " + instance.loginPage);
            System.out.println("   auth.forbidden.page = " + instance.forbiddenPage);
            
        } catch (Exception e) {
            System.err.println("❌ Erreur lors du chargement de " + CONFIG_FILE + " : " + e.getMessage());
            System.out.println("   Utilisation des valeurs par défaut.");
        }
    }
    
    public static AuthConfig getInstance() {
        if (instance == null) {
            instance = new AuthConfig();
        }
        return instance;
    }
    
    public String getAuthSessionKey() {
        return authSessionKey;
    }
    
    public String getRoleSessionKey() {
        return roleSessionKey;
    }
    
    public String getLoginPage() {
        return loginPage;
    }
    
    public String getForbiddenPage() {
        return forbiddenPage;
    }
    
    /**
     * Vérifie si l'utilisateur est authentifié
     */
    public boolean isAuthenticated(java.util.Map<String, Object> session) {
        if (session == null || session.isEmpty()) {
            return false;
        }
        
        Object authValue = session.get(authSessionKey);
        
        // Si la valeur est un Boolean, vérifier qu'elle est true
        if (authValue instanceof Boolean) {
            return (Boolean) authValue;
        }
        
        // Sinon, l'utilisateur est authentifié si la clé existe et n'est pas null
        return authValue != null;
    }
    
    /**
     * Récupère le rôle de l'utilisateur depuis la session
     */
    public String getUserRole(java.util.Map<String, Object> session) {
        if (session == null || session.isEmpty()) {
            return null;
        }
        
        Object roleValue = session.get(roleSessionKey);
        return roleValue != null ? roleValue.toString() : null;
    }
    
    /**
     * Vérifie si l'utilisateur a l'un des rôles autorisés
     */
    public boolean hasRole(java.util.Map<String, Object> session, String[] allowedRoles) {
        if (allowedRoles == null || allowedRoles.length == 0) {
            return true; // Aucune restriction de rôle
        }
        
        String userRole = getUserRole(session);
        if (userRole == null) {
            return false;
        }
        
        // Vérifier si le rôle de l'utilisateur est dans la liste autorisée
        for (String allowedRole : allowedRoles) {
            if (allowedRole.equalsIgnoreCase(userRole.trim())) {
                return true;
            }
        }
        
        return false;
    }
}