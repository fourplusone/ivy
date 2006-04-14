/**
 * This file is subject to the licence found in LICENCE.TXT in the root directory of the project.
 * Copyright Jayasoft 2005 - All rights reserved

 * Implementation of a VFS repository
 * 
 * @author glen
 * @author Matt Inger
 * @author Stephen Nesbitt
 * 
 */

package fr.jayasoft.ivy.repository.vfs;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.vfs.FileContent;
import org.apache.commons.vfs.FileSystemException;
import org.apache.commons.vfs.impl.StandardFileSystemManager;

import fr.jayasoft.ivy.repository.AbstractRepository;
import fr.jayasoft.ivy.repository.Resource;
import fr.jayasoft.ivy.repository.TransferEvent;
import fr.jayasoft.ivy.util.CopyProgressEvent;
import fr.jayasoft.ivy.util.CopyProgressListener;
import fr.jayasoft.ivy.util.FileUtil;
import fr.jayasoft.ivy.util.Message;


public class VfsRepository extends AbstractRepository {
	/**
	 * Name of the resource defining the Ivy VFS Repo configuration.
	 */
	private static final String IVY_VFS_CONFIG = "ivy_vfs.xml";
	private StandardFileSystemManager _manager = null;

	/**
	 * Create a new Ivy VFS Repository Instance
	 *
	 */
	public VfsRepository() {		
		try {
			/*
			 * The DefaultFileSystemManager gets its configuration from the jakarta-vfs-common
			 * implementation which includes the res and tmp schemes which are of no use to use here.
			 * Using StandardFileSystemManager lets us specify which schemes to support as well as 
			 * providing a mechanism to change this support without recompilation.
			 */
			_manager = new StandardFileSystemManager();
			_manager.setConfiguration(getClass().getResource(IVY_VFS_CONFIG));
			_manager.init();

			// Generate and print a list of available schemes
			Message.verbose("Available VFS schemes...");
			String[] schemes = _manager.getSchemes();
			Arrays.sort(schemes);
			for (int i = 0; i < schemes.length; i++) {
				Message.verbose("VFS Supported Scheme: " + schemes[i]);
			}
		} catch (FileSystemException e) {
			/*
			 * If our attempt to initialize a VFS Repository fails we log the failure
			 * but continue on. Given that an Ivy instance may involve numerous
			 * different repository types, it seems overly cautious to throw a runtime
			 * exception on the initialization failure of just one repository type.
			 */
			Message.error("Unable to initialize VFS repository manager!");
			Message.error(e.getLocalizedMessage());
			e.printStackTrace();
		}
	}

	private final CopyProgressListener _progress = new CopyProgressListener() {
		public void start(CopyProgressEvent evt) {
			fireTransferStarted();
		}
		
		public void progress(CopyProgressEvent evt) {
			fireTransferProgress(evt.getBuffer(), evt.getReadBytes());
		}
		
		public void end(CopyProgressEvent evt) {
			fireTransferCompleted(evt.getBuffer(), evt.getReadBytes());
		}
	};
	
	
	protected void finalize() {
		_manager.close();
	}

	/**
	 * Get a VfsResource
	 * 
	 * @param source a <code>String</code> identifying a VFS Resource
	 * @throws code>IOException</code> on failure
	 * @see "Supported File Systems in the jakarta-commons-vfs documentation"
	 */
	public Resource getResource(String vfsURI) throws IOException {
		return new VfsResource(vfsURI, _manager);
	}
	
	/**
	 * Transfer a VFS Resource from the repository to the local file system.
	 * 
	 * @param srcVfsURI a <code>String</code> identifying the VFS resource to be fetched
	 * @param destination a <code>File</code> identifying the destination file
	 * @throws <code>IOException</code> on failure
	 * @see "Supported File Systems in the jakarta-commons-vfs documentation"
	 */
	public void get(String srcVfsURI, File destination) throws IOException {
		VfsResource src = new VfsResource(srcVfsURI, _manager);
		fireTransferInitiated(src, TransferEvent.REQUEST_GET);
		try {
			FileContent content = src.getContent();
            if (content == null) {
                throw new IllegalArgumentException("invalid vfs uri "+srcVfsURI+": no content found");
            }
            FileUtil.copy(content.getInputStream(), destination, _progress);
		} catch (IOException ex) {
			fireTransferError(ex);
			throw ex;
		} catch (RuntimeException ex) {
			fireTransferError(ex);
			throw ex;
		}
	}
	
	/**
	 * Return a listing of the contents of a parent directory. Listing is a set
	 * of strings representing VFS URIs.
	 * 
	 * @param a <code>String</code> providing identifyiong a VFS provided resource
	 * @throws <code>IOException</code> on failure.
	 * @see "Supported File Systems in the jakarta-commons-vfs documentation"
	 */
	public List list(String vfsURI) throws IOException {
		VfsResource res = new VfsResource(vfsURI, _manager);
		return res.getChildren();
	};
	
	
	
	/**
	 * Transfer an Ivy resource to a VFS repository
	 * 
	 * @param source a <code>File</code> indentifying the local file to transfer to the repository
	 * @param vfsURI a <code>String</code> identifying the destination VFS Resource.
	 * @param overwrite whether to overwrite an existing resource.
	 * @throws <code>IOException</code> on failure.
	 * @see "Supported File Systems in the jakarta-commons-vfs documentation"
	 * 
	 */
	public void put(File source, String vfsURI, boolean overwrite) throws IOException {
		VfsResource dest = new VfsResource(vfsURI, _manager);
		fireTransferInitiated(dest, TransferEvent.REQUEST_PUT);
		if (dest.physicallyExists() && ! overwrite) {
			throw new IOException("Cannot copy. Destination file: " + dest.getName() + " exists and overwrite not set.");
		}
        if (dest.getContent() == null) {
            throw new IllegalArgumentException("invalid vfs uri "+vfsURI+" to put data to: resource has no content");
        }
		
		FileUtil.copy(new FileInputStream(source),
				       dest.getContent().getOutputStream(),
				       _progress);
	}
	

}