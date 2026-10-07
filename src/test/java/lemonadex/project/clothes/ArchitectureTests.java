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
    private static final JavaClasses CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS).importPackages(ROOT);

    @Test
    void layersHaveNoDependencyCycles() {
        slices().matching(ROOT + ".(*)..").should().beFreeOfCycles().check(CLASSES);
    }

    @Test
    void mvcDependenciesFollowControllerServiceRepository() {
        layeredArchitecture().consideringOnlyDependenciesInLayers()
                .layer("Controller").definedBy(ROOT + ".controller..")
                .layer("Service").definedBy(ROOT + ".service..")
                .layer("Repository").definedBy(ROOT + ".repository..")
                .layer("Model").definedBy(ROOT + ".model..")
                .layer("DTO").definedBy(ROOT + ".dto..")
                .layer("Mapper").definedBy(ROOT + ".mapper..")
                .layer("Security").definedBy(ROOT + ".security..")
                .whereLayer("Controller").mayNotBeAccessedByAnyLayer()
                .whereLayer("Service").mayOnlyBeAccessedByLayers("Controller", "Security")
                .whereLayer("Repository").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("DTO").mayOnlyBeAccessedByLayers("Controller", "Service", "Mapper", "Security")
                .whereLayer("Mapper").mayOnlyBeAccessedByLayers("Service")
                .whereLayer("Model").mayOnlyBeAccessedByLayers("Controller", "Service", "Repository", "DTO", "Mapper")
                .check(CLASSES);
    }

    @Test
    void modelsAndRepositoriesDoNotDependOnWebOrServices() {
        noClasses().that().resideInAPackage(ROOT + ".model..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..", ROOT + ".service..", ROOT + ".repository..",
                        ROOT + ".dto..", ROOT + ".config..", ROOT + ".security..",
                        "org.springframework.web..", "org.springframework.security..").check(CLASSES);
        noClasses().that().resideInAPackage(ROOT + ".repository..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        ROOT + ".controller..", ROOT + ".service..", ROOT + ".dto..",
                        "org.springframework.web..", "org.springframework.security..").check(CLASSES);
    }

    @Test
    void controllersAndDtosDoNotAccessPersistenceEntities() {
        noClasses().that().resideInAnyPackage(ROOT + ".controller..", ROOT + ".dto..")
                .should().dependOnClassesThat().areAnnotatedWith(Entity.class).check(CLASSES);
    }

    @Test
    void mvcComponentsBelongToTheirLayers() {
        classes().that().areAnnotatedWith(RestController.class)
                .should().resideInAPackage(ROOT + ".controller..").check(CLASSES);
        classes().that().areAnnotatedWith(Service.class)
                .should().resideInAPackage(ROOT + ".service..").check(CLASSES);
        classes().that().areAssignableTo(JpaRepository.class)
                .should().resideInAPackage(ROOT + ".repository..").check(CLASSES);
        classes().that().areAnnotatedWith(Entity.class)
                .should().resideInAPackage(ROOT + ".model..").check(CLASSES);
    }
}
