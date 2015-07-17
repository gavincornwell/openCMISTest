package com.gavincornwell;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.apache.chemistry.opencmis.client.api.CmisObject;
import org.apache.chemistry.opencmis.client.api.Document;
import org.apache.chemistry.opencmis.client.api.Folder;
import org.apache.chemistry.opencmis.client.api.ItemIterable;
import org.apache.chemistry.opencmis.client.api.ObjectId;
import org.apache.chemistry.opencmis.client.api.ObjectType;
import org.apache.chemistry.opencmis.client.api.Repository;
import org.apache.chemistry.opencmis.client.api.Session;
import org.apache.chemistry.opencmis.client.api.SessionFactory;
import org.apache.chemistry.opencmis.client.runtime.SessionFactoryImpl;
import org.apache.chemistry.opencmis.commons.PropertyIds;
import org.apache.chemistry.opencmis.commons.SessionParameter;
import org.apache.chemistry.opencmis.commons.data.ContentStream;
import org.apache.chemistry.opencmis.commons.enums.BindingType;
import org.apache.chemistry.opencmis.commons.enums.VersioningState;

/**
 * Unit test for exercising various CMIS operations.
 */
public class OpenCMISClientTest extends TestCase
{
    // TODO: Upgrade to JUnit 4 and use annotations
    //       Use beforeTest to setup the session
    
    /**
     * Create the test case
     *
     * @param testName name of the test case
     */
    public OpenCMISClientTest(String testName)
    {
        super(testName);
    }

    /**
     * @return the suite of tests being tested
     */
    public static Test suite()
    {
        return new TestSuite(OpenCMISClientTest.class);
    }

    /**
     * Tests connecting to a CMIS repository and retrieving the children of the root folder.
     */
    public void testConnection()
    {
        Map<String, String> params = this.getConnectionParameters(null);
        
        // list the repositories
        SessionFactory sf = SessionFactoryImpl.newInstance();
        List<Repository> repos = sf.getRepositories(params);
        assertTrue("There should be at least one repository!", repos.size() > 0);
        
        System.out.println("There are " + repos.size() + " repositories.");
        String repoId = null;
        for (Repository repo : repos)
        {
            System.out.println("Repository ID: " + repo.getId());
            
            if (repoId == null)
            {
                repoId = repo.getId();
            }
        }
        
        // create a session using the first repo found above
        params.put(SessionParameter.REPOSITORY_ID, repoId);
        Session s = sf.createSession(params);
        assertNotNull("Expected a session to be created", s);
        System.out.println("Session created.");
        
        // retrieve the root folder
        Folder root = s.getRootFolder();
        assertNotNull("Expected a root folder", root);
        String rootName = root.getName();
        System.out.println("Root folder is named: " + rootName);
        
        // retrieve and list the children
        System.out.println("Retrieving children...");
        ItemIterable<CmisObject> kids = root.getChildren();
        assertTrue("There should be at least one child node!", kids.getTotalNumItems() > 0);
        System.out.println("There are " + kids.getTotalNumItems() + " children.");
        for (CmisObject obj : kids)
        {
            System.out.println(obj.getName());
        }
    }
    
    public void testTypeDefinitions() throws Exception
    {
        Map<String, String> params = this.getConnectionParameters("-default-");
        
        // create a session
        SessionFactory sf = SessionFactoryImpl.newInstance();
        Session s = sf.createSession(params);
        assertNotNull("Expected a session to be created", s);
        System.out.println("Session created.");
        
        ObjectType documentTypeDefinition = s.getTypeDefinition("cmis:document");
        assertNotNull("Expected to receive a document type definition", documentTypeDefinition);
        assertTrue("Expected a display name of Document", documentTypeDefinition.getDisplayName().equals("Document"));
        
        ObjectType exifAspectDefinition = s.getTypeDefinition("P:exif:exif");
        assertNotNull("Expected to receive an exif aspect definition", exifAspectDefinition);
        assertTrue("Expected a display name of EXIF", exifAspectDefinition.getDisplayName().equals("EXIF"));
    }
    
    /**
     * Test checkout and checkin of a document
     */
    public void testCheckoutIn() throws Exception
    {
        Map<String, String> params = this.getConnectionParameters("-default-");
        
        // create a session
        SessionFactory sf = SessionFactoryImpl.newInstance();
        Session s = sf.createSession(params);
        assertNotNull("Expected a session to be created", s);
        System.out.println("Session created.");
        
        // create a folder in the root of the repo
        String folderName = "testFolder" + System.currentTimeMillis();
        Map<String, String> folderProps = new HashMap<String, String>(2);
        folderProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:folder");
        folderProps.put(PropertyIds.NAME, folderName);
        Folder testFolder = s.getRootFolder().createFolder(folderProps);
        System.out.println("Created folder " + folderName + " with id of " + testFolder.getId());
        
        // create a file in the folder
        String fileName = "test.txt";
        String mimetype = "text/plain; charset=UTF-8";
        String content = "This is some test content.";
        byte[] buf = content.getBytes("UTF-8");
        ByteArrayInputStream input = new ByteArrayInputStream(buf);
        ContentStream contentStream = s.getObjectFactory().createContentStream(fileName, buf.length, mimetype, input);
        Map<String, Object> fileProps = new HashMap<String, Object>();
        fileProps.put(PropertyIds.OBJECT_TYPE_ID, "cmis:document");
        fileProps.put(PropertyIds.NAME, fileName);
        Document doc = testFolder.createDocument(fileProps, contentStream, VersioningState.MAJOR);
        System.out.println("Created 'test.txt' with id of " + doc.getId());
        
        // checkout the file
        ObjectId pwcId = doc.checkOut();
        System.out.println("Checked out test.txt, received pwc with id of " + pwcId.getId());
        
        // retrieve the pwc
        Document pwc = (Document)s.getObject(pwcId);
        
        // checkin the file
        content = "This is some updated test content.";
        buf = content.getBytes("UTF-8");
        input = new ByteArrayInputStream(buf);
        contentStream = s.getObjectFactory().createContentStream(fileName, buf.length, mimetype, input);
        String versionComment = "v1.1";
        pwc.checkIn(false, null, contentStream, versionComment);
        System.out.println("Checked in test.txt");
        
        // retrieve the versions of the file
        List<Document> versions = doc.getAllVersions();
        System.out.println("test.txt has " + versions.size() + " versions");
    }
    
    private Map<String, String> getConnectionParameters(String repoId)
    {
        // setup parameters
        Map<String, String> params = new HashMap<String, String>(8);
        
        params.put(SessionParameter.USER, "admin");
        params.put(SessionParameter.PASSWORD, "admin");
        
        //params.put(SessionParameter.ATOMPUB_URL, "http://cmis.alfresco.com/service/cmis");
        //params.put(SessionParameter.ATOMPUB_URL, "http://localhost:8080/alfresco/cmisatom");
        params.put(SessionParameter.ATOMPUB_URL, "http://localhost:8080/alfresco/api/-default-/public/cmis/versions/1.0/atom");
        
        if (repoId != null && repoId.length() > 0)
        {
            params.put(SessionParameter.REPOSITORY_ID, repoId);
        }
        
        params.put(SessionParameter.BINDING_TYPE, BindingType.ATOMPUB.value());
        
        return params;
    }
}
