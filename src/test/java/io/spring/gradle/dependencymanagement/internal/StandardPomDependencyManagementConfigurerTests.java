/*
 * Copyright 2014-2022 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.spring.gradle.dependencymanagement.internal;

import java.io.File;
import java.util.Arrays;
import java.util.Collections;

import groovy.util.Node;
import groovy.util.XmlParser;
import groovy.xml.XmlUtil;
import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.artifacts.repositories.MavenArtifactRepository;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.Test;

import io.spring.gradle.dependencymanagement.NodeAssert;
import io.spring.gradle.dependencymanagement.internal.DependencyManagementSettings.PomCustomizationSettings;
import io.spring.gradle.dependencymanagement.internal.maven.MavenPomResolver;
import io.spring.gradle.dependencymanagement.internal.pom.Coordinates;
import io.spring.gradle.dependencymanagement.internal.pom.PomResolver;
import io.spring.gradle.dependencymanagement.internal.properties.MapPropertySource;

/**
 * Tests for {@link StandardPomDependencyManagementConfigurer}.
 *
 * @author Andy Wilkinson
 */
public class StandardPomDependencyManagementConfigurerTests {

    private final Project project;

    private final DependencyManagementContainer dependencyManagement;

    private final PomResolver pomResolver;

    public StandardPomDependencyManagementConfigurerTests() {
        this.project = new ProjectBuilder().build();
        this.project.getRepositories().mavenCentral();
        this.pomResolver = new MavenPomResolver(this.project, new DependencyManagementConfigurationContainer(project));
        this.dependencyManagement = new DependencyManagementContainer(this.project, this.pomResolver);
    }

    @Test
    public void anImportedBomIsImportedInThePom() throws Exception {
        this.dependencyManagement.importBom(null, new Coordinates("io.spring.platform", "platform-bom", "1.0.3.RELEASE"), new MapPropertySource(Collections.<String, String>emptyMap()));
        String pom = configuredPom();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/groupId").isEqualTo("io.spring.platform");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/artifactId").isEqualTo("platform-bom");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/version").isEqualTo("1.0.3.RELEASE");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/type").isEqualTo("pom");
    }

    @Test
    public void multipleImportsAreImportedInTheOppositeOrderToWhichTheyWereImported() throws Exception {
        this.project.getRepositories().maven(new Action<MavenArtifactRepository>() {

            @Override
            public void execute(MavenArtifactRepository repository) {
                repository.setUrl(new File("src/test/resources/maven-repo").getAbsoluteFile());
            }

        });
        this.dependencyManagement.importBom(null, new Coordinates("test", "bravo-pom-customization-bom", "1.0"),
                new MapPropertySource(Collections.<String, String>emptyMap()));
        this.dependencyManagement.importBom(null, new Coordinates("test", "alpha-pom-customization-bom", "1.0"),
                new MapPropertySource(Collections.<String, String>emptyMap()));
        String pom = configuredPom();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/groupId").isEqualTo("test");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/artifactId").isEqualTo("alpha-pom-customization-bom");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/version").isEqualTo("1.0");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/type").isEqualTo("pom");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/groupId").isEqualTo("test");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/artifactId").isEqualTo("bravo-pom-customization-bom");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/version").isEqualTo("1.0");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/type").isEqualTo("pom");
    }

    @Test
    public void customizationOfPublishedPomsCanBeDisabled() throws Exception {
        this.dependencyManagement.importBom(null, new Coordinates("io.spring.platform", "platform-bom", "1.0.3.RELEASE"), new MapPropertySource(Collections.<String, String>emptyMap()));
        PomCustomizationSettings settings = new PomCustomizationSettings();
        settings.setEnabled(false);
        String pom = configuredPom(settings);
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").isEmpty();
    }

    @Test
    public void individualDependencyManagementIsAddedToThePom() throws Exception {
        this.dependencyManagement.addManagedVersion(null,  "org.springframework", "spring-core", "4.1.3.RELEASE", Collections.<Exclusion>emptyList());
        String pom = configuredPom();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/groupId").isEqualTo("org.springframework");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/artifactId").isEqualTo("spring-core");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/version").isEqualTo("4.1.3.RELEASE");
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency/scope").isNull();
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency/type").isNull();
    }

    @Test
    public void dependencyManagementCanBeAddedToAPomWithExistingDependencyManagement() throws Exception {
        this.dependencyManagement.importBom(null, new Coordinates("io.spring.platform", "platform-bom", "1.0.3.RELEASE"), new MapPropertySource(Collections.<String, String>emptyMap()));
        String pom = configuredPom("<project><dependencyManagement><dependencies></dependencies></dependencyManagement></project>");
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").hasSize(1);
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/groupId").isEqualTo("io.spring.platform");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/artifactId").isEqualTo("platform-bom");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/version").isEqualTo("1.0.3.RELEASE");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/type").isEqualTo("pom");
    }

    @Test
    public void dependencyManagementExclusionsAreAddedToThePom() throws Exception {
        this.dependencyManagement.addManagedVersion(null,  "org.springframework", "spring-core", "4.1.3.RELEASE", Arrays.asList(new Exclusion("commons-logging", "commons-logging"), new Exclusion("com.example", "example")));
        String pom = configuredPom();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/groupId").isEqualTo("org.springframework");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/artifactId").isEqualTo("spring-core");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/version").isEqualTo("4.1.3.RELEASE");
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency/scope").isNull();
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency/type").isNull();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/exclusions/exclusion[groupId/text() = 'commons-logging']/artifactId").isEqualTo("commons-logging");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency/exclusions/exclusion[groupId/text() = 'com.example']/artifactId").isEqualTo("example");
    }

    @Test
    public void overridingAVersionPropertyResultsInDependencyOverridesInPom() throws Exception {
        this.dependencyManagement.importBom(null, new Coordinates("org.springframework.boot", "spring-boot-dependencies",
                "1.5.9.RELEASE"), new MapPropertySource(Collections.<String, String>emptyMap()));
        this.project.getExtensions().getExtraProperties().set("spring.version", "4.3.5.RELEASE");
        String pom = configuredPom();
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").hasSize(21);
        for (int i = 1; i < 21; i++) {
            assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[" + i + "]/groupId").isEqualTo("org.springframework");
            assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[" + i + "]/version").isEqualTo("4.3.5.RELEASE");
        }
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[21]/groupId").isEqualTo("org.springframework.boot");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[21]/artifactId").isEqualTo("spring-boot-dependencies");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[21]/version").isEqualTo("1.5.9.RELEASE");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[21]/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[21]/type").isEqualTo("pom");
    }

    @Test
    public void whenAVersionOverrideResultsInABomWithManagementOfANewDependencyItsManagementAppearsInThePom() throws Exception {
        this.dependencyManagement.importBom(null, new Coordinates("org.springframework.boot", "spring-boot-dependencies",
                "1.5.9.RELEASE"), new MapPropertySource(Collections.<String, String>emptyMap()));
        this.project.getExtensions().getExtraProperties().set("spring.version", "5.0.2.RELEASE");
        String pom = configuredPom();
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").hasSize(22);
        for (int i = 1; i < 22; i++) {
            assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[" + i + "]/groupId").isEqualTo("org.springframework");
            assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[" + i + "]/version").isEqualTo("5.0.2.RELEASE");
        }
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency[artifactId/text() = 'spring-context-indexer']").isNotNull();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[22]/groupId").isEqualTo("org.springframework.boot");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[22]/artifactId").isEqualTo("spring-boot-dependencies");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[22]/version").isEqualTo("1.5.9.RELEASE");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[22]/scope").isEqualTo("import");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[22]/type").isEqualTo("pom");
    }

    @Test
    public void whenAnImportedBomOverridesDependencyManagementFromAnotherImportedBomAnExplicitOverrideIsNotAdded() throws Exception {
        this.project.getRepositories().maven(new Action<MavenArtifactRepository>() {

            @Override
            public void execute(MavenArtifactRepository repository) {
                repository.setUrl(new File("src/test/resources/maven-repo").getAbsoluteFile());
            }

        });
        this.dependencyManagement.importBom(null, new Coordinates("test", "first-alpha-dependency-management", "1.0"),
                new MapPropertySource(Collections.<String, String>emptyMap()));
        this.dependencyManagement.importBom(null, new Coordinates("test", "second-alpha-dependency-management", "1.0"),
                new MapPropertySource(Collections.<String, String>emptyMap()));
        String pom = configuredPom();
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").hasSize(2);
    }

    @Test
    public void dependencyManagementIsExpandedToCoverDependenciesWithAClassifier() throws Exception {
        this.dependencyManagement.addManagedVersion(null, "org.apache.logging.log4j", "log4j-core", "2.6", Collections.<Exclusion>emptyList());
        String pom = configuredPom("<project><dependencies><dependency><groupId>org.apache.logging.log4j</groupId><artifactId>log4j-core</artifactId><classifier>test</classifier></dependency></dependencies></project>");
        assertThat(pom).nodesAtPath("//project/dependencyManagement/dependencies/dependency").hasSize(2);
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/groupId").isEqualTo("org.apache.logging.log4j");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/artifactId").isEqualTo("log4j-core");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[1]/version").isEqualTo("2.6");
        assertThat(pom).nodeAtPath("//project/dependencyManagement/dependencies/dependency[1]/classifier").isNull();
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/groupId").isEqualTo("org.apache.logging.log4j");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/artifactId").isEqualTo("log4j-core");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/version").isEqualTo("2.6");
        assertThat(pom).textAtPath("//project/dependencyManagement/dependencies/dependency[2]/classifier").isEqualTo("test");
    }

    private String configuredPom() throws Exception {
        return configuredPom(new PomCustomizationSettings());
    }

    private String configuredPom(String existingPom) throws Exception {
        return configuredPom(existingPom, new PomCustomizationSettings());
    }

    private String configuredPom(PomCustomizationSettings settings) throws Exception {
        return configuredPom("<project></project>", settings);
    }

    private String configuredPom(String existingPom, PomCustomizationSettings settings) throws Exception {
        Node pom = new XmlParser().parseText(existingPom);
        new StandardPomDependencyManagementConfigurer(this.dependencyManagement.getGlobalDependencyManagement(), settings, this.pomResolver, this.project).configurePom(pom);
        return XmlUtil.serialize(pom);
    }

    private NodeAssert assertThat(String xml) {
        return new NodeAssert(xml);
    }

}
