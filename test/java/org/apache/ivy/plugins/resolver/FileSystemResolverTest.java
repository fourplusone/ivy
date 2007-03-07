/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */
package org.apache.ivy.plugins.resolver;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.util.Date;
import java.util.GregorianCalendar;

import junit.framework.TestCase;

import org.apache.ivy.core.cache.CacheManager;
import org.apache.ivy.core.event.EventManager;
import org.apache.ivy.core.module.descriptor.Artifact;
import org.apache.ivy.core.module.descriptor.DefaultArtifact;
import org.apache.ivy.core.module.descriptor.DefaultDependencyDescriptor;
import org.apache.ivy.core.module.id.ModuleRevisionId;
import org.apache.ivy.core.report.ArtifactDownloadReport;
import org.apache.ivy.core.report.DownloadReport;
import org.apache.ivy.core.report.DownloadStatus;
import org.apache.ivy.core.resolve.DownloadOptions;
import org.apache.ivy.core.resolve.ResolveData;
import org.apache.ivy.core.resolve.ResolveEngine;
import org.apache.ivy.core.resolve.ResolveOptions;
import org.apache.ivy.core.resolve.ResolvedModuleRevision;
import org.apache.ivy.core.search.ModuleEntry;
import org.apache.ivy.core.search.OrganisationEntry;
import org.apache.ivy.core.search.RevisionEntry;
import org.apache.ivy.core.settings.IvySettings;
import org.apache.ivy.core.sort.SortEngine;
import org.apache.ivy.plugins.latest.LatestRevisionStrategy;
import org.apache.ivy.plugins.latest.LatestTimeStrategy;
import org.apache.ivy.util.FileUtil;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.taskdefs.Delete;


/**
 * 
 */
public class FileSystemResolverTest extends TestCase {

    private static final String FS = System.getProperty("file.separator");
    private static final String IVY_PATTERN = "test"+FS+"repositories"+FS+"1"+FS+"[organisation]"+FS+"[module]"+FS+"ivys"+FS+"ivy-[revision].xml";
    private IvySettings _settings;
    private ResolveEngine _engine;
    private ResolveData _data;
    private File _cache;
	private CacheManager _cacheManager;
    
    
    public FileSystemResolverTest() {
        setupLastModified();
    }
    
    protected void setUp() throws Exception {
    	_settings = new IvySettings();
        _engine = new ResolveEngine(_settings, new EventManager(), new SortEngine(_settings));
        _cache = new File("build/cache");
        _data = new ResolveData(_engine, new ResolveOptions().setCache(CacheManager.getInstance(_settings, _cache)));
        _cache.mkdirs();
        _cacheManager = new CacheManager(_settings, _cache);
        _settings.setDefaultCache(_cache);
    }
    
    private void setupLastModified() {
        // change important last modified dates cause svn doesn't keep them
        long minute = 60 * 1000;
        long time = new GregorianCalendar().getTimeInMillis() - (4 * minute);
        new File("test/repositories/1/org1/mod1.1/ivys/ivy-1.0.xml").setLastModified(time);
        time += minute;
        new File("test/repositories/1/org1/mod1.1/ivys/ivy-1.0.1.xml").setLastModified(time);
        time += minute;
        new File("test/repositories/1/org1/mod1.1/ivys/ivy-1.1.xml").setLastModified(time);
        time += minute;
        new File("test/repositories/1/org1/mod1.1/ivys/ivy-2.0.xml").setLastModified(time);
    }

    protected void tearDown() throws Exception {
        Delete del = new Delete();
        del.setProject(new Project());
        del.setDir(_cache);
        del.execute();
    }

    public void testFixedRevision() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
        
        
        // test to ask to download
        DefaultArtifact artifact = new DefaultArtifact(mrid, pubdate, "mod1.1", "jar", "jar");
        DownloadReport report = resolver.download(new Artifact[] {artifact}, getDownloadOptions(false));
        assertNotNull(report);
        
        assertEquals(1, report.getArtifactsReports().length);
        
        ArtifactDownloadReport ar = report.getArtifactReport(artifact);
        assertNotNull(ar);
        
        assertEquals(artifact, ar.getArtifact());
        assertEquals(DownloadStatus.SUCCESSFUL, ar.getDownloadStatus());

        // test to ask to download again, should use cache
        report = resolver.download(new Artifact[] {artifact}, getDownloadOptions(false));
        assertNotNull(report);
        
        assertEquals(1, report.getArtifactsReports().length);
        
        ar = report.getArtifactReport(artifact);
        assertNotNull(ar);
        
        assertEquals(artifact, ar.getArtifact());
        assertEquals(DownloadStatus.NO, ar.getDownloadStatus());
    }

    private DownloadOptions getDownloadOptions(boolean useOrigin) {
		return new DownloadOptions(_settings, _cacheManager, null, useOrigin);
	}

	public void testMaven2() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        resolver.setM2compatible(true);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern("test/repositories/m2/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]");
        resolver.addArtifactPattern("test/repositories/m2/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]");
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org.apache", "test", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);

        mrid = ModuleRevisionId.newInstance("org.apache.unknown", "test", "1.0");
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNull(rmr);
        resolver.reportFailure();
    }

    public void testChecksum() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        
        resolver.addIvyPattern("test/repositories/checksums/[module]/[artifact]-[revision].[ext]");
        resolver.addArtifactPattern("test/repositories/checksums/[module]/[artifact]-[revision].[ext]");
        
        resolver.setChecksums("sha1, md5");
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("test", "allright", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        DownloadReport dr = resolver.download(rmr.getDescriptor().getAllArtifacts(), getDownloadOptions(false));
        assertEquals(2, dr.getArtifactsReports(DownloadStatus.SUCCESSFUL).length);

        resolver.setChecksums("md5");
        mrid = ModuleRevisionId.newInstance("test", "badivycs", "1.0");
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNull(rmr);
        resolver.setChecksums("none");
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        dr = resolver.download(new Artifact[] {new DefaultArtifact(mrid, rmr.getPublicationDate(), mrid.getName(), "jar", "jar")}, getDownloadOptions(false));
        assertEquals(1, dr.getArtifactsReports(DownloadStatus.SUCCESSFUL).length);

        resolver.setChecksums("md5");
        mrid = ModuleRevisionId.newInstance("test", "badartcs", "1.0");
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        dr = resolver.download(new Artifact[] {new DefaultArtifact(mrid, rmr.getPublicationDate(), mrid.getName(), "jar", "jar")}, getDownloadOptions(false));
        assertEquals(1, dr.getArtifactsReports(DownloadStatus.FAILED).length);
        
        resolver.setChecksums("");
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        dr = resolver.download(new Artifact[] {new DefaultArtifact(mrid, rmr.getPublicationDate(), mrid.getName(), "jar", "jar")}, getDownloadOptions(false));
        assertEquals(1, dr.getArtifactsReports(DownloadStatus.SUCCESSFUL).length);
    }

    public void testCheckModified() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        _settings.addResolver(resolver);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern("test"+FS+"repositories"+FS+"checkmodified"+FS+"ivy-[revision].xml");
        File modify = new File("test/repositories/checkmodified/ivy-1.0.xml");
        FileUtil.copy(new File("test/repositories/checkmodified/ivy-1.0-before.xml"), modify, null, true);
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());
                
        // updates ivy file in repository
        FileUtil.copy(new File("test/repositories/checkmodified/ivy-1.0-after.xml"), modify, null, true);
        pubdate = new GregorianCalendar(2005, 4, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        
        // should not get the new version
        resolver.setCheckmodified(false);
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime(), rmr.getPublicationDate());

        // should now get the new version
        resolver.setCheckmodified(true);
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testNoRevision() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        _settings.addResolver(resolver);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern("test"+FS+"repositories"+FS+"norevision"+FS+"ivy-[module].xml");
        resolver.addArtifactPattern("test"+FS+"repositories"+FS+"norevision"+FS+"[artifact].[ext]");
        File modify = new File("test/repositories/norevision/ivy-mod1.1.xml");
        File artifact = new File("test/repositories/norevision/mod1.1.jar");
        
        // 'publish' 'before' version
        FileUtil.copy(new File("test/repositories/norevision/ivy-mod1.1-before.xml"), modify, null, true);
        FileUtil.copy(new File("test/repositories/norevision/mod1.1-before.jar"), artifact, null, true);
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "latest.integration");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(ModuleRevisionId.newInstance("org1", "mod1.1", "1.0"), rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());
        
        Artifact[] artifacts = rmr.getDescriptor().getArtifacts("default");
        File archiveFileInCache = _cacheManager.getArchiveFileInCache(artifacts[0]);
        resolver.download(artifacts, getDownloadOptions(false));
        assertTrue(archiveFileInCache.exists());
        BufferedReader r = new BufferedReader(new FileReader(archiveFileInCache));
        assertEquals("before", r.readLine());
        r.close();
                
        // updates ivy file and artifact in repository 
        FileUtil.copy(new File("test/repositories/norevision/ivy-mod1.1-after.xml"), modify, null, true);
        FileUtil.copy(new File("test/repositories/norevision/mod1.1-after.jar"), artifact, null, true);
        pubdate = new GregorianCalendar(2005, 4, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        // no need to update new artifact timestamp cause it isn't used
        
        // should get the new version even if checkModified is false, beacause we ask a latest.integration
        resolver.setCheckmodified(false);
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(ModuleRevisionId.newInstance("org1", "mod1.1", "1.1"), rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());

        artifacts = rmr.getDescriptor().getArtifacts("default");
        archiveFileInCache = _cacheManager.getArchiveFileInCache(artifacts[0]);
        
        assertFalse(archiveFileInCache.exists());

        // should download the new artifact
        artifacts = rmr.getDescriptor().getArtifacts("default");
        resolver.download(artifacts, getDownloadOptions(false));
        assertTrue(archiveFileInCache.exists());
        r = new BufferedReader(new FileReader(archiveFileInCache));
        assertEquals("after", r.readLine());
        r.close();
    }

    public void testChanging() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        _settings.addResolver(resolver);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern("test"+FS+"repositories"+FS+"checkmodified"+FS+"ivy-[revision].xml");
        resolver.addArtifactPattern("test"+FS+"repositories"+FS+"checkmodified"+FS+"[artifact]-[revision].[ext]");
        File modify = new File("test/repositories/checkmodified/ivy-1.0.xml");
        File artifact = new File("test/repositories/checkmodified/mod1.1-1.0.jar");
        
        // 'publish' 'before' version
        FileUtil.copy(new File("test/repositories/checkmodified/ivy-1.0-before.xml"), modify, null, true);
        FileUtil.copy(new File("test/repositories/checkmodified/mod1.1-1.0-before.jar"), artifact, null, true);
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());
        
        Artifact[] artifacts = rmr.getDescriptor().getArtifacts("default");
        resolver.download(artifacts, getDownloadOptions(false));
        File archiveFileInCache = _cacheManager.getArchiveFileInCache(artifacts[0]);
        assertTrue(archiveFileInCache.exists());
        BufferedReader r = new BufferedReader(new FileReader(archiveFileInCache));
        assertEquals("before", r.readLine());
        r.close();
                
        // updates ivy file and artifact in repository 
        FileUtil.copy(new File("test/repositories/checkmodified/ivy-1.0-after.xml"), modify, null, true);
        FileUtil.copy(new File("test/repositories/checkmodified/mod1.1-1.0-after.jar"), artifact, null, true);
        pubdate = new GregorianCalendar(2005, 4, 1, 11, 0, 0).getTime();
        modify.setLastModified(pubdate.getTime());
        // no need to update new artifact timestamp cause it isn't used
        
        // should not get the new version: checkmodified is false and edpendency is not told to be a changing one
        resolver.setCheckmodified(false);
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime(), rmr.getPublicationDate());

        assertTrue(archiveFileInCache.exists());
        r = new BufferedReader(new FileReader(archiveFileInCache));
        assertEquals("before", r.readLine());
        r.close();

        // should now get the new version cause we say it's a changing one
        rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false, true), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        assertEquals(pubdate, rmr.getPublicationDate());

        assertFalse(archiveFileInCache.exists());

        artifacts = rmr.getDescriptor().getArtifacts("default");
        resolver.download(artifacts, getDownloadOptions(false));
        assertTrue(archiveFileInCache.exists());
        r = new BufferedReader(new FileReader(archiveFileInCache));
        assertEquals("after", r.readLine());
        r.close();
    }

    public void testLatestTime() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        resolver.setLatestStrategy(new LatestTimeStrategy());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "2.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "mod1.1", "latest.integration"), false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2005, 1, 15, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testLatestRevision() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        resolver.setLatestStrategy(new LatestRevisionStrategy());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "2.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "mod1.1", "latest.integration"), false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2005, 1, 15, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testRelativePath() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(new File("src/java").getAbsolutePath()+"/../../"+IVY_PATTERN);
        resolver.addArtifactPattern("src/../test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        resolver.setLatestStrategy(new LatestRevisionStrategy());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "2.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "mod1.1", "latest.integration"), false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2005, 1, 15, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testFormattedLatestTime() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        resolver.setLatestStrategy(new LatestTimeStrategy());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.1");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "mod1.1", "1+"), false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2005, 0, 2, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testFormattedLatestRevision() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        resolver.setLatestStrategy(new LatestRevisionStrategy());
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.1");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(ModuleRevisionId.newInstance("org1", "mod1.1", "1+"), false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2005, 0, 2, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
    }

    public void testPublish() throws Exception {
        try {
            FileSystemResolver resolver = new FileSystemResolver();
            resolver.setName("test");
            resolver.setSettings(_settings);
            assertEquals("test", resolver.getName());
            
            resolver.addIvyPattern("test"+FS+"repositories"+FS+"1"+FS+"[organisation]"+FS+"[module]"+FS+"[revision]"+FS+"[artifact].[ext]");
            resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]");
            
            ModuleRevisionId mrid = ModuleRevisionId.newInstance("myorg", "mymodule", "myrevision");
            Artifact ivyArtifact = new DefaultArtifact(mrid, new Date(), "ivy", "ivy", "xml");
            Artifact artifact = new DefaultArtifact(mrid, new Date(), "myartifact", "mytype", "myext");
            File src = new File("test/repositories/ivyconf.xml");
            resolver.publish(ivyArtifact, src, false);
            resolver.publish(artifact, src, false);
            
            assertTrue(new File("test/repositories/1/myorg/mymodule/myrevision/ivy.xml").exists());
            assertTrue(new File("test/repositories/1/myorg/mymodule/mytypes/myartifact-myrevision.myext").exists());
        } finally {
            Delete del = new Delete();
            del.setProject(new Project());
            del.setDir(new File("test/repositories/1/myorg"));
            del.execute();
        }
    }
    
    public void testListing() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[ext]");

        OrganisationEntry[] orgs = resolver.listOrganisations();
        ResolverTestHelper.assertOrganisationEntries(resolver, new String[] {"org1", "org2", "org6", "org9", "orgfailure", "yourorg"}, orgs);
        
        OrganisationEntry org = ResolverTestHelper.getEntry(orgs, "org1");
        ModuleEntry[] mods = resolver.listModules(org);
        ResolverTestHelper.assertModuleEntries(resolver, org, new String[] {"mod1.1", "mod1.2", "mod1.3", "mod1.4", "mod1.5", "mod1.6"}, mods);

        ModuleEntry mod = ResolverTestHelper.getEntry(mods, "mod1.1");
        RevisionEntry[] revs = resolver.listRevisions(mod);
        ResolverTestHelper.assertRevisionEntries(resolver, mod, new String[] {"1.0", "1.0.1", "1.1", "2.0"}, revs);

        mod = ResolverTestHelper.getEntry(mods, "mod1.2");
        revs = resolver.listRevisions(mod);
        ResolverTestHelper.assertRevisionEntries(resolver, mod, new String[] {"1.0", "1.1", "2.0", "2.1", "2.2"}, revs);
    }

    public void testDownloadWithUseOriginIsTrue() throws Exception {
        FileSystemResolver resolver = new FileSystemResolver();
        resolver.setName("test");
        resolver.setSettings(_settings);
        assertEquals("test", resolver.getName());
        
        resolver.addIvyPattern(IVY_PATTERN);
        resolver.addArtifactPattern("test/repositories/1/[organisation]/[module]/[type]s/[artifact]-[revision].[type]");
        
        ModuleRevisionId mrid = ModuleRevisionId.newInstance("org1", "mod1.1", "1.0");
        ResolvedModuleRevision rmr = resolver.getDependency(new DefaultDependencyDescriptor(mrid, false), _data);
        assertNotNull(rmr);
        
        assertEquals(mrid, rmr.getId());
        Date pubdate = new GregorianCalendar(2004, 10, 1, 11, 0, 0).getTime();
        assertEquals(pubdate, rmr.getPublicationDate());
        
        
        // test to ask to download
        DefaultArtifact artifact = new DefaultArtifact(mrid, pubdate, "mod1.1", "jar", "jar");
        DownloadReport report = resolver.download(new Artifact[] {artifact}, getDownloadOptions(true));
        assertNotNull(report);
        
        assertEquals(1, report.getArtifactsReports().length);
        
        ArtifactDownloadReport ar = report.getArtifactReport(artifact);
        assertNotNull(ar);
        
        assertEquals(artifact, ar.getArtifact());
        assertEquals(DownloadStatus.NO, ar.getDownloadStatus());
    }
}
