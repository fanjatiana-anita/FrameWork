package method_annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation pour spécifier les rôles autorisés à accéder à une méthode.
 * Si la liste est vide, tous les utilisateurs authentifiés peuvent accéder.
 * Implique automatiquement @Authentified
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface Role {
    String[] value() default {};
}