/**
 * BSD-style license; for more info see http://pmd.sourceforge.net/license.html
 */

package net.sourceforge.pmd.build;

import static net.sourceforge.pmd.build.util.ConfigUtil.getString;
import static net.sourceforge.pmd.build.util.XmlUtil.createXmlBackbone;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import javax.xml.transform.dom.DOMSource;

import org.apache.commons.io.FilenameUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import net.sourceforge.pmd.build.filefilter.DirectoryFileFilter;
import net.sourceforge.pmd.build.filefilter.RulesetFilenameFilter;
import net.sourceforge.pmd.build.util.FileUtil;
import net.sourceforge.pmd.build.util.XmlUtil;
import net.sourceforge.pmd.build.xml.RulesetFileTemplater;

/**
 * A small class to convert files from pmd rulesets fmt to xdoc fmt
 *
 * @author Romain PELISSE, belaran@gmail.com
 *
 */
public class RuleSetToDocs implements PmdBuildTools {

    private static final Logger LOGGER = Logger.getLogger(PmdBuildException.class.toString());

    private String indexRuleSetFilename = getString("pmd.build.config.index.filename");
    private String mergedRuleSetFilename = getString("pmd.build.config.mergedRuleset.filename");

    private String rulesDirectory;
    private String targetDirectory;
    private String siteXml;
    private String siteXmlTarget;
    private URL[] runtimeClasspath;
    private RuntimeRulePropertiesAnalyzer ruleAnalyzer;

    public URL[] getRuntimeClasspath() {
        return runtimeClasspath;
    }

    public void setRuntimeClasspath(URL[] runtimeClasspath) {
        this.runtimeClasspath = runtimeClasspath;
    }

    public String getSiteXmlTarget() {
        return siteXmlTarget;
    }

    public void setSiteXmlTarget(String siteXmlTarget) {
        this.siteXmlTarget = siteXmlTarget;
    }

    private RulesetFileTemplater xmlFileTemplater;

    public String getRulesDirectory() {
        return rulesDirectory;
    }

    public void setRulesDirectory(String rulesDirectory) {
        this.rulesDirectory = rulesDirectory;
    }

    public String getIndexRuleSetFilename() {
        return indexRuleSetFilename;
    }

    public void setIndexRuleSetFilename(String indexRuleSetFilename) {
        this.indexRuleSetFilename = indexRuleSetFilename;
    }

    public String getMergedRuleSetFilename() {
        return mergedRuleSetFilename;
    }

    public void setMergedRuleSetFilename(String mergedRuleSetFilename) {
        this.mergedRuleSetFilename = mergedRuleSetFilename;
    }

    public String getTargetDirectory() {
        return targetDirectory;
    }

    public void setTargetDirectory(String targetDirectory) {
        this.targetDirectory = targetDirectory;
    }

    public RulesetFileTemplater getXmlFileTemplater() {
        return xmlFileTemplater;
    }

    public void setXmlFileTemplater(RulesetFileTemplater xmlFileTemplater) {
        this.xmlFileTemplater = xmlFileTemplater;
    }

    public String getSiteXml() {
        return siteXml;
    }

    public void setSiteXml(String siteXml) {
        this.siteXml = siteXml;
    }

    /*
     * <ol> <li>Initialize the xml factory,</li> <li>Check if target exist (or
     * try to create it).</li> </ol>
     */
    private void init() throws PmdBuildException {
        FileUtil.createDirIfMissing(targetDirectory);
        xmlFileTemplater = new RulesetFileTemplater(rulesDirectory);
        ruleAnalyzer = new RuntimeRulePropertiesAnalyzer(runtimeClasspath);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Merge xsl:" + xmlFileTemplater.getMergeRulesetXsl());
        }
    }

    public void convertRulesets() throws PmdBuildException {
        init();
        File rulesDir = new File(rulesDirectory);
        if (rulesDir.exists() && rulesDir.isDirectory()) {
            recursivelyProcessSubFolder(processAllXDocsFilesFromDir(rulesDir));
        } else if (!rulesDir.exists()) {
            throw new PmdBuildException("The rulesets directory specified '" + rulesDirectory + "' does not exist");
        } else if (!rulesDir.isDirectory()) {
            throw new PmdBuildException(
                    "The rulesets directory '" + rulesDirectory + "' provided is not a directory !");
        }
    }

    private void recursivelyProcessSubFolder(File rulesDir) throws PmdBuildException {
        for (File folder : FileUtil.filterFilesFrom(rulesDir, new DirectoryFileFilter())) {
            recursivelyProcessSubFolder(processAllXDocsFilesFromDir(folder));
        }
    }

    private File processAllXDocsFilesFromDir(File rulesDir) throws PmdBuildException {
        for (File ruleset : FileUtil.filterFilesFrom(rulesDir, new RulesetFilenameFilter())) {
            processXDocFile(ruleset);
        }
        return rulesDir;
    }

    private File buildTransformedRulesetDirectory(File ruleset) {
        return new File(this.targetDirectory + File.separator + ruleset.getParentFile().getName() + File.separator
                + FilenameUtils.getBaseName(ruleset.getName()) + ".md");
    }

    private void processXDocFile(File ruleset) throws PmdBuildException {
        final File targetFile = buildTransformedRulesetDirectory(ruleset);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Processing file " + ruleset + " into " + targetFile.getAbsolutePath());
        }
        FileUtil.ensureTargetDirectoryExist(targetFile);
        convertRuleSetFile(ruleset, targetFile);
    }

    private void convertRuleSetFile(File ruleset, File target) throws PmdBuildException {
        try {
            DOMSource dom = XmlUtil.createDomSourceFrom(new FileInputStream(ruleset));
            Document document = (Document) dom.getNode();
            NodeList rules = document.getElementsByTagName("rule");
            for (int i = 0; i < rules.getLength(); i++) {
                Node rule = rules.item(i);
                ruleAnalyzer.analyze(document, rule);

                escapeTextContent(findChildren(rule, "example"));
                List<Node> properties = findChildren(rule, "properties");
                for (Node prop : properties) {
                    List<Node> property = findChildren(prop, "property");
                    for (Node n : property) {
                        if (((Element) n).getAttribute("name").equals("xpath")) {
                            escapeTextContent(findChildren(n, "value"));
                        }
                    }
                }
            }

            xmlFileTemplater.transform(dom, target, xmlFileTemplater.getRulesetToDocsXsl());
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException(e);
        }
    }

    private static List<Node> findChildren(Node parent, String childName) {
        List<Node> result = new ArrayList<Node>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (isElement(child, childName)) {
                result.add(child);
            }
        }
        return result;
    }

    private static boolean isElement(Node node, String name) {
        return node.getNodeType() == Node.ELEMENT_NODE && name.equals(node.getNodeName());
    }

    private static void escapeTextContent(Collection<Node> nodes) {
        for (Node node : nodes) {
            escapeTextContent(node);
        }
    }

    private static void escapeTextContent(Node node) {
        String content = node.getTextContent();
        content = content.replaceAll("&", "&amp;");
        content = content.replaceAll("<", "&lt;");
        node.setTextContent(content);
    }

    private void addRulesetsToSiteXml(DOMSource backbone) {
        File menu = FileUtil.createTempFile("menu.xml");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("menu file:" + menu.getAbsolutePath());
        }
        xmlFileTemplater.transform(backbone, menu, xmlFileTemplater.getCreateRulesetMenuXsl());
        File site = FileUtil.createTempFile("site.xml");
        Map<String, String> parameters = new HashMap<String, String>(1);
        parameters.put("menufile", menu.getAbsoluteFile().toString());
        File sitePre = new File(siteXml);
        xmlFileTemplater.transform(sitePre, site, xmlFileTemplater.getAddToSiteDescriptorXsl(), parameters);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("new site describ:" + site.getAbsolutePath());
        }
        FileUtil.move(site, new File(getSiteXmlTarget()));

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("deleting file:" + site.getAbsolutePath());
        }
        FileUtil.deleteFile(site);

        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("deleting file:" + site.getAbsolutePath());
        }
        FileUtil.deleteFile(menu);
    }

    private DOMSource createMergedFile(File mergedFile) {
        DOMSource backbone = createXmlBackbone(xmlFileTemplater);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine(XmlUtil.transformDomToString(backbone));
        }
        xmlFileTemplater.transform(backbone, mergedFile, xmlFileTemplater.getMergeRulesetXsl());
        // Fix, removing the xmlns field of each ruleset in the generated xml
        // file.
        FileUtil.replaceAllInFile(mergedFile, "xmlns=\"http://pmd.sourceforge.net/ruleset/2.0.0\"", "");
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Creating index file:" + this.indexRuleSetFilename + ", using merged file:"
                    + mergedFile.toString());
        }
        return backbone;
    }

    public void preSiteGeneration() {
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Merging all rules into " + this.mergedRuleSetFilename);
        }
        File mergedFile = new File(
                this.targetDirectory + File.separator + FileUtil.pathToParent + File.separator + mergedRuleSetFilename);
        DOMSource backbone = createMergedFile(mergedFile);
        if (LOGGER.isLoggable(Level.FINE)) {
            LOGGER.fine("Creating index file:" + this.indexRuleSetFilename + ", using merged file:"
                    + mergedFile.toString());
        }
        // Create index from ruleset merge
        xmlFileTemplater.transform(mergedFile, new File(this.targetDirectory + File.separator + indexRuleSetFilename),
                xmlFileTemplater.getGenerateIndexXsl());
        // Create menu file from merge
        addRulesetsToSiteXml(backbone);
    }
}
