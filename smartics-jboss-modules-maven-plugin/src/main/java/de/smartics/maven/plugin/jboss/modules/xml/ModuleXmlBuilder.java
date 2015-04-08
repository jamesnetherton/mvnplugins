/*
 * Copyright 2013-2014 smartics, Kronseder & Reiner GmbH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.smartics.maven.plugin.jboss.modules.xml;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.*;

import de.smartics.maven.plugin.jboss.modules.util.Logger;
import org.apache.commons.lang.ObjectUtils;
import org.apache.commons.lang.StringUtils;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.Namespace;
import org.eclipse.aether.artifact.Artifact;
import org.eclipse.aether.graph.Dependency;

import de.smartics.maven.plugin.jboss.modules.descriptor.ApplyToDependencies;
import de.smartics.maven.plugin.jboss.modules.descriptor.ApplyToModule;
import de.smartics.maven.plugin.jboss.modules.descriptor.DependenciesDescriptor;
import de.smartics.maven.plugin.jboss.modules.descriptor.ModuleDescriptor;
import de.smartics.maven.plugin.jboss.modules.domain.ExecutionContext;
import de.smartics.maven.plugin.jboss.modules.domain.SlotStrategy;
import edu.emory.mathcs.backport.java.util.Collections;
import org.jdom2.output.Format;
import org.jdom2.output.XMLOutputter;

/**
 * Creates <code>module.xml</code> descriptors for JBoss modules.
 */
public final class ModuleXmlBuilder
{
  // ********************************* Fields *********************************

  // --- constants ------------------------------------------------------------

  /**
   * The default namespace for the <code>module.xml</code> descriptor.
   */
  public static final Namespace NS = Namespace
      .getNamespace("urn:jboss:module:1.1");

  // --- members --------------------------------------------------------------

  /**
   * The context and configuration to control the building of XML files.
   */
  private final ExecutionContext context;

  /**
   * The module to build.
   */
  private final ModuleDescriptor module;

  /**
   * The dependencies to reference.
   */
  private final Collection<Dependency> dependencies;

  /**
   * The XML document.
   */
  private final Document document;

  /**
   * The root element of the document.
   */
  private final Element root;

  /**
   * A helper class to parse XML fragments.
   */
  private final XmlFragmentParser xmlFragmentParser = new XmlFragmentParser();

  // ****************************** Initializer *******************************

  // ****************************** Constructors ******************************

  /**
   * Default constructor.
   *
   * @param context the context and configuration to control the building of XML
   *          files.
   * @param module the module to build.
   * @param dependencies the dependencies to reference.
   */
  public ModuleXmlBuilder(final ExecutionContext context,
      final ModuleDescriptor module, final Collection<Dependency> dependencies)
  {
    this.context = context;
    this.module = module;
    this.dependencies = dependencies;

    root = new Element("module", NS);
    root.setAttribute("name", module.getName());
    final String slot = calcSlot(context, module, dependencies);
    if (!SlotStrategy.MAIN_SLOT.equals(slot))
    {
      root.setAttribute("slot", slot);
    }
    document = new Document(root);
  }

  // ****************************** Inner Classes *****************************

  /**
   * Helper to sort artifact lists.
   */
  private static final class SortElement implements Comparable<SortElement>
  {
    /**
     * The key used for sorting.
     */
    private final String key;

    /**
     * The dependency to be sorted.
     */
    private final Dependency dependency;

    private SortElement(final String key, final Dependency dependency)
    {
      this.key = key;
      this.dependency = dependency;
    }

    /**
     * Returns the hash code of the object.
     *
     * @return the hash code.
     */
    @Override
    public int hashCode()
    {
      return ObjectUtils.hashCode(key);
    }

    /**
     * Returns <code>true</code> if the given object is semantically equal to
     * the given object, <code>false</code> otherwise.
     *
     * @param object the instance to compare to.
     * @return <code>true</code> if the given object is semantically equal to
     *         the given object, <code>false</code> otherwise.
     */
    @Override
    public boolean equals(final Object object)
    {
      if (this == object)
      {
        return true;
      }
      else if (object == null || getClass() != object.getClass())
      {
        return false;
      }

      final ModuleXmlBuilder.SortElement other =
          (ModuleXmlBuilder.SortElement) object;

      return ObjectUtils.equals(key, other.key);
    }

    @Override
    public int compareTo(final SortElement o)
    {
      return key.compareTo(o.key);
    }

    @Override
    public String toString()
    {
      return String.valueOf(key) + ": " + String.valueOf(dependency);
    }
  }

  // ********************************* Methods ********************************

  // --- init -----------------------------------------------------------------

  private static String calcSlot(final ExecutionContext context,
      final ModuleDescriptor module, final Collection<Dependency> dependencies)
  {
    final SlotStrategy strategy = context.getSlotStrategy();
    final String moduleSlot = module.getSlot();
    final String defaultSlot = context.getDefaultSlot();
    final Artifact artifact = calcArtifact(dependencies);
    final String slot = strategy.calcSlot(defaultSlot, moduleSlot, artifact);
    return slot;
  }

  private static Artifact calcArtifact(final Collection<Dependency> dependencies)
  {
    if (dependencies != null && !dependencies.isEmpty())
    {
      final Dependency dependency = dependencies.iterator().next();
      final Artifact artifact = dependency.getArtifact();
      return artifact;
    }
    return null;
  }

  // --- get&set --------------------------------------------------------------

  // --- business -------------------------------------------------------------

  /**
   * Builds the document.
   *
   * @return the XML document.
   */
  public Document build()
  {
    addMainClass(module);
    addProperties(module);
    addResources(module, dependencies);
    addDependencies(module, dependencies);
    addExports(module);

    return document;
  }

  private void addMainClass(final ModuleDescriptor module)
  {
    final String xml = module.getApplyToModule().getMainClassXml();
    if (xml != null)
    {
      final Element element = xmlFragmentParser.parse(xml);
      root.addContent(element);
    }
  }

  private void addProperties(final ModuleDescriptor module)
  {
    final List<String> xmls = module.getApplyToModule().getPropertiesXml();
    if (xmls.isEmpty())
    {
      return;
    }

    final Element propertiesElement = new Element("properties", NS);
    for (final String xml : xmls)
    {
      final Element element = xmlFragmentParser.parse(xml);
      propertiesElement.addContent(element);
    }
    root.addContent(propertiesElement);
  }

  private void addResources(ModuleDescriptor module, final Collection<Dependency> dependencies)
  {
    final Element resources = new Element("resources", NS);

    List<String> resourceRootsXml = module.getApplyToModule().getResourceRootsXml();
    for (final String xml : resourceRootsXml)
    {
      final Element element = xmlFragmentParser.parse(xml);
      resources.addContent(element);
    }

    if (!dependencies.isEmpty())
    {

      final List<SortElement> sorted = createSortedResources(dependencies);
      for (final SortElement element : sorted)
      {
        final Element resource = new Element("resource-root", NS);
        final String fileName = element.key;
        resource.setAttribute("path", fileName);
        resources.addContent(resource);
      }
    }

    if( !resources.getChildren().isEmpty() ) {
        root.addContent(resources);
    }
  }

  private List<SortElement> createSortedResources(
      final Collection<Dependency> dependencies)
  {
    final List<SortElement> sorted =
        new ArrayList<SortElement>(dependencies.size());
    for (final Dependency dependency : dependencies)
    {
      final Artifact artifact = dependency.getArtifact();
      final File file = artifact.getFile();
      if (file != null)
      {
        final String fileName = file.getName();
        sorted.add(new SortElement(fileName, dependency));
      }
    }
    Collections.sort(sorted);
    return sorted;
  }

  private void addDependencies(final ModuleDescriptor module,
      final Collection<Dependency> dependencies)
  {
    final ApplyToModule applyToModule = module.getApplyToModule();
    final List<String> staticDependencies = applyToModule.getDependenciesXml();

    Logger.log("\n\n~~~~~~~~~~~~~~~~~~~~~ ADD DEPENDENCIES FOR MODULE " + module.getName() + " START ~~~~~~~~~~~~~~~~~~~~~");
    Logger.log("INCLUDED ARTIFACTS:");
    for(Dependency dep : dependencies) {
      Logger.log("\t" + dep.toString());
    }

    if (!(dependencies.isEmpty() && staticDependencies.isEmpty())) {
      final Element dependenciesElement = new Element("dependencies", NS);

      addStaticDependencies(staticDependencies, dependenciesElement);
      addResolvedDependencies(module, dependencies, dependenciesElement);

      XMLOutputter outp = new XMLOutputter();
      outp.setFormat(Format.getPrettyFormat());
      StringWriter sw = new StringWriter();
      try {
        outp.output(dependenciesElement.getContent(), sw);
      } catch (IOException e) {
        e.printStackTrace();
      }
      StringBuffer sb = sw.getBuffer();
      Logger.log("\nBUILT MODULE DEPENDENCY XML:");
      Logger.log(sb.toString());

      root.addContent(dependenciesElement);
    }

    Logger.log("~~~~~~~~~~~~~~~~~~~~~ ADD DEPENDENCIES FOR MODULE " + module.getName() + " END ~~~~~~~~~~~~~~~~~~~~~\n\n");
  }

  private void addResolvedDependencies(final ModuleDescriptor module,
      final Collection<Dependency> dependencies,
      final Element dependenciesElement)
  {
    final Set<SortElement> sorted =
        createSortedDependencies(module, dependencies);

    final ApplyToDependencies apply = module.getApplyToDependencies();

    for (final SortElement element : sorted)
    {
      final String name = element.key;
      final Element moduleElement = new Element("module", NS);
      moduleElement.setAttribute("name", name);

      Logger.log("\nFINDING MATCHING MODULE DESCRIPTOR FOR: " + name);
      final DependenciesDescriptor dd = apply.getDescriptorThatMatches(name);

      Logger.log("\nADDING RESOLVED DEPENDENCIES:");
      if(isIncludableDependency(element, dd))
      {
        Logger.log("\tINCLUDING " + element.dependency.toString() + " SKIPPED = " + dd.getSkip() + ", OPTIONAL DD = " + dd.getOptional() + ", OPTIONAL MAVEN = " + element.dependency.isOptional());
        handleOptional(element, moduleElement, dd);
        handleExport(moduleElement, dd);
        handleServices(moduleElement, dd);
        handleSlot(module, element, moduleElement);
        dependenciesElement.addContent(moduleElement);
      } else {
        Logger.log("\tEXCLUDING " + element.dependency.toString() + " SKIPPED = " + dd.getSkip() + ", OPTIONAL DD = " + dd.getOptional() + ", OPTIONAL MAVEN = " + element.dependency.isOptional());
      }
    }
  }

  private boolean isIncludableDependency(final SortElement element, final DependenciesDescriptor dd)
  {
    /*
     * A dependency is considered NOT valid for inclusion within a module if:
     *   - The dependency is flagged as 'skipped'
     *   - The dependency is optional and the ignoreOptionalDependencies property is true
     */

    boolean isSkipped = dd.getSkip() != null && dd.getSkip() == true;
    boolean isOptional = (dd.getOptional() != null && dd.getOptional() == true) || element.dependency.isOptional();

    if(isSkipped) {
      return false;
    }

    if(isOptional && context.isIgnoreOptionalDependencies()) {
      return false;
    }

    return true;
  }

  private void handleOptional(final SortElement element,
      final Element moduleElement, final DependenciesDescriptor dd)
  {
    final Boolean ddOptional = dd.getOptional();
    if ((ddOptional != null && ddOptional)
        || (ddOptional == null || element.dependency.isOptional()))
    {
      moduleElement.setAttribute("optional", "true");
    }
  }

  private void handleExport(final Element moduleElement,
      final DependenciesDescriptor dd)
  {
    final Boolean ddExport = dd.getExport();
    if (ddExport != null && ddExport)
    {
      moduleElement.setAttribute("export", "true");
    }
  }

  private void handleServices(final Element moduleElement,
      final DependenciesDescriptor dd)
  {
    final String services = dd.getServices();
    if (services != null && !"none".equals(services))
    {
      moduleElement.setAttribute("services", services);
    }
  }

  private void handleSlot(final ModuleDescriptor module,
      final SortElement element, final Element moduleElement)
  {
    final SlotStrategy slotStrategy = context.getSlotStrategy();
    final Dependency dependency = element.dependency;
    final String defaultSlot = calcDefaultSlot(module, dependency);
    final String slot =
        slotStrategy.calcSlot(dependency.getArtifact(), defaultSlot);
    if (!SlotStrategy.MAIN_SLOT.equals(slot))
    {
      moduleElement.setAttribute("slot", slot);
    }
  }

  private Set<SortElement> createSortedDependencies(
      final ModuleDescriptor module, final Collection<Dependency> dependencies)
  {
    final Set<SortElement> sorted = new TreeSet<SortElement>();
    for (final Dependency dependency : dependencies)
    {
      final List<Dependency> resolvedDependencies = context.resolve(dependency);
      addSortedDependencies(sorted, module, resolvedDependencies);
    }
    return sorted;
  }

  private String calcDefaultSlot(final ModuleDescriptor module,
      final Dependency dependency)
  {
    final ModuleDescriptor depModule = context.getModule(dependency);
    final String depModuleSlot = depModule.getSlot();
    if (StringUtils.isNotBlank(depModuleSlot))
    {
      return depModuleSlot;
    }

    final boolean inheritSlot = module.getDirectives().getInheritSlot();
    if (inheritSlot)
    {
      final String moduleSlot = module.getSlot();
      if (StringUtils.isNotBlank(moduleSlot))
      {
        return moduleSlot;
      }
    }

    final String defaultSlot = context.getDefaultSlot();
    return defaultSlot;
  }

  // CHECKSTYLE:OFF
  private void addStaticDependencies(final List<String> staticDependencies,
      final Element dependenciesElement)
  {
    if (!staticDependencies.isEmpty())
    {
      Logger.log("\nADDING STATIC DEPENDENCIES:");
      for (final String xml : staticDependencies)
      {
        final Element element = xmlFragmentParser.parse(xml);
        dependenciesElement.addContent(element);
        Logger.log("\t" + element.getAttribute("name").getValue());
      }
    }
  }

  // CHECKSTYLE:ON

  private void addSortedDependencies(final Set<SortElement> sorted,
      final ModuleDescriptor owningModule, final List<Dependency> dependencies)
  {
    Logger.log("\nADD RESOLVED MODULE DEPENDENCIES TO: " + owningModule.getName());
    for (final Dependency dependency : dependencies)
    {
      try
      {
        final ModuleDescriptor module = context.getModule(dependency);
        final String name = module.getName();
        if (!name.equals(owningModule.getName()))
        {
          /*
           * It's possible for a module to have resources that share the same dependencies. Potentially these
           * dependencies could be declared differently in their respective POM files. E.g one resource may specify the
           * dependency as optional and another resource may declare it as being a mandatory.
           *
           * In this scenario, always assume that the dependency should be mandatory.
           */
          SortElement e = new SortElement(name, dependency);

          if(sorted.contains(e))
          {
            Iterator<SortElement> iter = sorted.iterator();
            while (iter.hasNext())
            {
              SortElement current = iter.next();

              // We are processing a non-optional dependency that has already been been added to the set as optional
              if(current.equals(e) && current.dependency.isOptional() && !dependency.isOptional())
              {
                // Remove so that it can be replaced later with a non-optional dependency
                sorted.remove(current);
                break;
              }
            }
          }
          boolean added = sorted.add(e);
          Logger.log("\t ADD MODULE DEPENDENCY " + name + " FOR ARTIFACT " + dependency.toString() + ", ADDED TO SET = " + added);
        }
      }
      catch (final IllegalArgumentException e)
      {
        context.getLog().error(
            String.format("Skipping '%s' referenced from module '%s'.",
                dependency.getArtifact().getArtifactId(),
                owningModule.getName()));
      }
    }
  }

  private void addExports(final ModuleDescriptor module2)
  {
    final String xml = module.getApplyToModule().getExportsXml();
    if (xml != null)
    {
      final Element element = xmlFragmentParser.parse(xml);
      root.addContent(element);
    }
  }

  // --- object basics --------------------------------------------------------

}
