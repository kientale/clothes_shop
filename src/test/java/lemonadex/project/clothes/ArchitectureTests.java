package lemonadex.project.clothes;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import jakarta.persistence.Entity;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.Architectures.layeredArchitecture;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

class ArchitectureTests {
    private static final String ROOT = "lemonadex.project.clothes";
    private static final String FEATURES = ROOT + ".features";
    private static final String COMMON = ROOT + ".common";
    private static final String CONTROLLER = FEATURES + ".*.controller..";
    private static final String SERVICE = FEATURES + ".*.service..";
    private static final String REPOSITORY = FEATURES + ".*.repository..";
    private static final String MODEL = FEATURES + ".*.model..";
    private static final String DTO = FEATURES + ".*.dto..";
    private static final String MAPPER = FEATURES + ".*.mapper..";
    private static final String SECURITY = FEATURES + ".*.security..";
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    @Test
    void featuresHaveNoDependencyCycles() {
        slices().matching(FEATURES + ".(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void sharedCodeDoesNotDependOnFeatures() {
        noClasses().that().resideInAPackage(COMMON + "..")
                .should().dependOnClassesThat().resideInAPackage(FEATURES + "..").check(CLASSES);
    }

    @Test
    void accountFeatureDoesNotDependOnAuthentication() {
        noClasses().that().resideInAPackage(FEATURES + ".account..")
                .should().dependOnClassesThat().resideInAPackage(FEATURES + ".auth..").check(CLASSES);
    }

    @Test
    void roleAndAccountFeaturesHaveNoReverseDependencies() {
        noClasses().that().resideInAPackage(FEATURES + ".role..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        FEATURES + ".account..", FEATURES + ".customer..", FEATURES + ".auth..").check(CLASSES);
        noClasses().that().resideInAPackage(FEATURES + ".account..")
                .should().dependOnClassesThat().resideInAPackage(FEATURES + ".customer..").check(CLASSES);
    }

    @Test
    void applicationComponentsBelongToFeaturesOrCommon() {
        classes().that().resideOutsideOfPackage(ROOT)
                .should().resideInAnyPackage(FEATURES + "..", COMMON + "..").check(CLASSES);
    }

    @Test
    void mvcDependenciesWithinFeaturesFollowControllerServiceRepository() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy(CONTROLLER)
                .layer("Service").definedBy(SERVICE)
                .layer("Repository").definedBy(REPOSITORY)
                .layer("Model").definedBy(MODEL, COMMON + ".model..")
                .layer("DTO").definedBy(DTO, COMMON + ".dto..")
                .layer("Mapper").definedBy(MAPPER)
                .layer("Security").definedBy(SECURITY)
                .layer("Web").definedBy(COMMON + ".exception..", COMMON + ".filter..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Security")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service", "Mapper", "Security", "Web")
                .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Model").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "DTO", "Mapper")
                .check(CLASSES);
    }

    @Test
    void modelsAndRepositoriesDoNotDependOnWebOrServices() {
        noClasses().that().resideInAnyPackage(MODEL, COMMON + ".model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        CONTROLLER, SERVICE, REPOSITORY, DTO, FEATURES + ".*.config..", SECURITY,
                        "org.springframework.web..", "org.springframework.security..").check(CLASSES);
        noClasses().that().resideInAPackage(REPOSITORY)
                .should().dependOnClassesThat().resideInAnyPackage(
                        CONTROLLER, SERVICE, DTO,
                        "org.springframework.web..", "org.springframework.security..").check(CLASSES);
    }

    @Test
    void controllersAndDtosDoNotAccessPersistenceEntities() {
        noClasses().that().resideInAnyPackage(CONTROLLER, DTO, COMMON + ".dto..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class).check(CLASSES);
    }

    @Test
    void mvcComponentsBelongToTheirFeatureLayers() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(CONTROLLER).check(CLASSES);
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage(SERVICE).check(CLASSES);
        classes().that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage(REPOSITORY).check(CLASSES);
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(MODEL).check(CLASSES);
    }
}
