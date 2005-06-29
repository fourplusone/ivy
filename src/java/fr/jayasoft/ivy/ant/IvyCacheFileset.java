/*
 * This file is subject to the license found in LICENCE.TXT in the root directory of the project.
 * 
 * #SNAPSHOT#
 */
package fr.jayasoft.ivy.ant;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.PatternSet.NameEntry;

import fr.jayasoft.ivy.Artifact;
import fr.jayasoft.ivy.Ivy;
import fr.jayasoft.ivy.ModuleId;
import fr.jayasoft.ivy.xml.XmlReportParser;

//TODO: refactor this class and IvyCacheFileset to extract common behaviour
public class IvyCacheFileset extends IvyTask {
    private String _conf;
    private String _setid;

    private String _organisation;
    private String _module;
    private boolean _haltOnFailure = true;
    private File _cache;
    private String _type;
    
    public String getConf() {
        return _conf;
    }
    
    public void setConf(String conf) {
        _conf = conf;
    }
    
    public String getModule() {
        return _module;
    }
    public void setModule(String module) {
        _module = module;
    }
    public String getOrganisation() {
        return _organisation;
    }
    public void setOrganisation(String organisation) {
        _organisation = organisation;
    }
    public boolean isHaltonfailure() {
        return _haltOnFailure;
    }
    public void setHaltonfailure(boolean haltOnFailure) {
        _haltOnFailure = haltOnFailure;
    }
    public File getCache() {
        return _cache;
    }
    public void setCache(File cache) {
        _cache = cache;
    }
    public String getSetid() {
        return _setid;
    }
    public void setSetid(String id) {
        _setid = id;
    }
    public String getType() {
        return _type;
    }
    public void setType(String type) {
        _type = type;
    }

    public void execute() throws BuildException {
        Ivy ivy = getIvyInstance();
        if (_setid == null) {
            throw new BuildException("setid is required in ivy cachefileset");
        }
        ensureResolved(isHaltonfailure());
        _conf = getProperty(_conf, ivy, "ivy.resolved.configurations");
        if (_conf.equals("*")) {
            _conf = getProperty(ivy, "ivy.resolved.configurations");
        }
        _organisation = getProperty(_organisation, ivy, "ivy.organisation");
        _module = getProperty(_module, ivy, "ivy.module");
        if (_cache == null) {
            _cache = ivy.getDefaultCache();
        }
        
        if (_organisation == null || _module == null) {
            throw new BuildException("no module id provided for ivy path: either call resolve, give paramaters to ivy:retrieve, or provide ivy.module and ivy.organisation properties");
        }
        try {
            XmlReportParser parser = new XmlReportParser();
            FileSet fileset = new FileSet();
            fileset.setProject(getProject());
            getProject().addReference(_setid, fileset);
            fileset.setDir(getCache());
            
            String[] confs = splitConfs(_conf);
            Collection all = new LinkedHashSet();
            for (int i = 0; i < confs.length; i++) {
                Artifact[] artifacts = parser.getArtifacts(new ModuleId(_organisation, _module), confs[i], _cache);
                all.addAll(Arrays.asList(artifacts));
            }
            for (Iterator iter = all.iterator(); iter.hasNext();) {
                Artifact artifact = (Artifact)iter.next();
                if (accept(artifact)) {
                    NameEntry ne = fileset.createInclude();
                    ne.setName(ivy.getArchivePathInCache(artifact));
                }
            }
        } catch (Exception ex) {
            throw new BuildException("impossible to build ivy cache fileset: "+ex.getMessage(), ex);
        }
        
    }

    private boolean accept(Artifact artifact) {
        return _type == null || _type.equals(artifact.getType());
    }

}