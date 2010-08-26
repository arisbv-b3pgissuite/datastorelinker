/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.b3p.datastorelinker.gui.stripes;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import javax.persistence.EntityManager;
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;
import net.sourceforge.stripes.action.Before;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.DontValidate;
import net.sourceforge.stripes.action.FileBean;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.action.StreamingResolution;
import net.sourceforge.stripes.controller.LifecycleStage;
import net.sourceforge.stripes.controller.StripesRequestWrapper;
import net.sourceforge.stripes.util.Log;
import nl.b3p.commons.jpa.JpaUtilServlet;
import nl.b3p.commons.stripes.Transactional;
import nl.b3p.datastorelinker.entity.File;
import nl.b3p.datastorelinker.json.JSONResolution;
import nl.b3p.datastorelinker.json.UploaderStatus;
import org.hibernate.Session;

/**
 *
 * @author Erik van de Pol
 */
@Transactional
public class FileAction extends DefaultAction {

    private final static Log log = Log.getInstance(FileAction.class);

    protected final static String SHAPE_EXT = ".shp";
    protected final static String ZIP_EXT = ".zip";


    private final static String CREATE_JSP = "/pages/main/file/create.jsp";
    private final static String LIST_JSP = "/pages/main/file/list.jsp";
    private final static String ADMIN_JSP = "/pages/management/fileAdmin.jsp";
    private final static String DIRCONTENTS_JSP = "/pages/main/file/filetreeConnector.jsp";

    private List<File> files;
    private List<File> directories;

    private Long selectedFileId;
    private String selectedFileIds;

    private FileBean filedata;
    //private Map<Integer, UploaderStatus> uploaderStatuses;
    private UploaderStatus uploaderStatus;
    private Long dir;

    public Resolution listDir() {
        log.debug(dir);
        
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        String directory = null;
        if (dir != null) {
            File directoryObject = (File)session.get(File.class, dir);
            log.debug(directoryObject.getDirectory());
            java.io.File directoryFile = new java.io.File(directoryObject.getDirectory(), directoryObject.getName());
            directory = directoryFile.getAbsolutePath();
        } else {
            directory = getUploadDirectory();
        }
        
        directories = session.createQuery("from File where directory = (:directory) and isDirectory = true order by name")
                .setParameter("directory", directory)
                .list();
        files = session.createQuery("from File where directory = (:directory) and isDirectory = false order by name")
                .setParameter("directory", directory)
                .list();

        filterOutShapeExtraFiles();

        //log.debug("dirs: " + directories.size());
        //log.debug("files: " + files.size());

        return new ForwardResolution(DIRCONTENTS_JSP);
    }

    protected void filterOutShapeExtraFiles() {
        String shapeName = null;
        for (File file : files) {
            if (file.getName().endsWith(SHAPE_EXT)) {
                shapeName = file.getName().substring(0, file.getName().length() - SHAPE_EXT.length());
            }
        }

        if (shapeName != null) {
            List<File> toBeIgnoredFiles = new ArrayList<File>();
            for (File file : files) {
                if (file.getName().startsWith(shapeName) && !file.getName().endsWith(SHAPE_EXT)) {
                    toBeIgnoredFiles.add(file);
                }
            }
            for (File file : toBeIgnoredFiles) {
                files.remove(file);
            }
        }
    }

    public Resolution admin() {
        list();
        return new ForwardResolution(ADMIN_JSP);
    }

    public Resolution list() {
        /*EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        files = session.createQuery("from File order by name").list();*/

        return new ForwardResolution(LIST_JSP);
    }

    public Resolution delete() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        log.debug(selectedFileIds);

        JSONArray selectedFileIdsJSON = JSONArray.fromObject(selectedFileIds);
        for (Object fileIdObj : selectedFileIdsJSON) {
            Long fileId = Long.valueOf((String)fileIdObj);
            
            File file = (File)session.get(File.class, fileId);

            deleteImpl(file);
        }
        return list();

        /*if (deleteSuccess) {
            session.delete(file);
            return list();
        } else {
            log.error("File could not be deleted from the filesystem: " + fsFile.getAbsolutePath());
            // silent fail?
            //throw new Exception();
            return list();
        }*/
    }

    protected void deleteImpl(File file) {
        if (file != null) {
            EntityManager em = JpaUtilServlet.getThreadEntityManager();
            Session session = (Session)em.getDelegate();

            java.io.File fsFile = new java.io.File(file.getDirectory(), file.getName());
            boolean deleteSuccess = fsFile.delete();
            session.delete(file);

            deleteExtraShapeFilesInDir(file);
            deleteDirIfDir(file);
        }
    }

    private void deleteExtraShapeFilesInDir(File file) {
        if (file.getName().endsWith(SHAPE_EXT)) {
            EntityManager em = JpaUtilServlet.getThreadEntityManager();
            Session session = (Session)em.getDelegate();

            List<File> extraShapeFilesInDir = session.createQuery(
                    "from File where directory = :directory and name like :shapename")
                    .setParameter("directory", file.getDirectory())
                    .setParameter("shapename",
                        file.getName().substring(0, file.getName().length() - SHAPE_EXT.length())
                        + ".___")
                    .list();

            for (File extraShapeFile : extraShapeFilesInDir) {
                if (!extraShapeFile.getIsDirectory()) {
                    deleteImpl(extraShapeFile);
                }
            }
        }
    }

    protected void deleteDirIfDir(File dir) {
        // can be null if we tried to delete a directory first and then
        // one or more (recursively) deleted files within it.
        if (dir != null && dir.getIsDirectory() == true) {
            EntityManager em = JpaUtilServlet.getThreadEntityManager();
            Session session = (Session)em.getDelegate();

            // TODO: eigenlijk moet file/dir verwijderen van de server en file/dir uit de db verwijderen een atomaire operatie zijn.
            java.io.File fsFile = new java.io.File(dir.getDirectory(), dir.getName());

            List<File> filesInDir = session.createQuery("from File where directory = :directory")
                    .setParameter("directory", fsFile.getAbsolutePath())
                    .list();

            log.debug(filesInDir);
            for (File fileInDir : filesInDir) {
                deleteImpl(fileInDir);
            }

            // dir must be empty when deleting it
            boolean deleteDirSuccess = fsFile.delete();
        }
    }

    @DontValidate
    public Resolution create() {
        return new ForwardResolution(CREATE_JSP);
    }

    public Resolution createComplete() {
        return list();
    }

    @SuppressWarnings("unused")
    @Before(stages = LifecycleStage.BindingAndValidation)
    private void rehydrate() {
        StripesRequestWrapper req = StripesRequestWrapper.findStripesWrapper(getContext().getRequest());

        try {
            if (req.isMultipart()) {
                filedata = req.getFileParameterValue("Filedata");
            } else if (req.getParameter("status") != null) {
                log.debug("qwe: " + req.getParameter("status"));
                JSONObject jsonObject = JSONObject.fromObject(req.getParameter("status"));
                uploaderStatus = (UploaderStatus)JSONObject.toBean(jsonObject, UploaderStatus.class);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
    }

    @DefaultHandler
    public Resolution upload() {
        // TODO: geüploadede file blijft nu in geheugen staan tot nader order.
        // misschien in de temp dir zetten. kost wel weer tijd.

        //TODO URL Encode the messages
        String errorMsg = null;

        if (filedata != null) {

            log.debug("Filedata: " + filedata.getFileName());
            try {
                java.io.File dirFile = new java.io.File(getUploadDirectory());
                if (!dirFile.exists())
                    dirFile.mkdir();
                
                java.io.File tempFile = java.io.File.createTempFile(filedata.getFileName() + ".", null);
                //java.io.File tempFile = new java.io.File(dirFile, filedata.getFileName());
                filedata.save(tempFile);

                if (isZipFile(filedata.getFileName())) {
                    java.io.File zipDir = new java.io.File(getUploadDirectory(), getZipName(filedata.getFileName()));
                    extractZip(tempFile, zipDir);
                } else {

                    java.io.File destinationFile = new java.io.File(dirFile, filedata.getFileName());
                    tempFile.renameTo(destinationFile);

                    log.info("Saved file " + destinationFile.getAbsolutePath() + ", Successfully!");

                    File file = saveFile(destinationFile);
                    selectedFileId = file.getId();
                }

            } catch (IOException e) {
                errorMsg = e.getMessage();
                log.error("Error while writing file :" + filedata.getFileName() + " / " + errorMsg);
                return new StreamingResolution("text/xml", errorMsg);
            }
            return createComplete();
            //return new StreamingResolution("text/xml", "success");
        }
        return new StreamingResolution("text/xml", "An unknown error has occurred!");
    }

    /**
     * Extract a file {name}.zip.*.tmp to zipDir
     * @param tempFile {name}.zip.*.tmp
     * @param zipDir zipDir
     * @throws IOException
     */
    private void extractZip(java.io.File tempFile, java.io.File zipDir) throws IOException {
        if (!tempFile.exists()) {
            return;
        }

        if (!zipDir.exists()) {
            zipDir.mkdirs();
            saveDir(zipDir);
        }

        byte[] buffer = new byte[1024];
        ZipInputStream zipinputstream = null;
        
        try {
            zipinputstream = new ZipInputStream(new FileInputStream(tempFile));

            ZipEntry zipentry = null;
            while ((zipentry = zipinputstream.getNextEntry()) != null) {
                log.debug("extractZip zipentry name: " + zipentry.getName());
                
                java.io.File newFile = new java.io.File(zipDir, zipentry.getName());

                if (zipentry.isDirectory()) {
                    // ZipInputStream does not work recursively
                    // files within this directory will be encountered as a later zipEntry
                    newFile.mkdirs();
                    saveDir(newFile);
                } else if (isZipFile(zipentry.getName())) {
                    java.io.File tempZipFile = java.io.File.createTempFile(zipentry.getName() + ".", null);
                    java.io.File newZipDir = new java.io.File(zipDir, getZipName(zipentry.getName()));

                    copyZipEntryTo(zipinputstream, tempZipFile, buffer);

                    extractZip(tempZipFile, newZipDir);
                } else {
                    // TODO: is valid file in zip (delete newFile if necessary)
                    copyZipEntryTo(zipinputstream, newFile, buffer);
                    saveFile(newFile);
                }
                
                zipinputstream.closeEntry();
            }
        } finally {
            if (zipinputstream != null) {
                zipinputstream.close();
            }

            boolean deleteSuccess = tempFile.delete();
            /*if (!deleteSuccess)
                log.warn("Could not delete: " + tempFile.getAbsolutePath());*/
        }
    }

    private void copyZipEntryTo(ZipInputStream zipinputstream, java.io.File newFile, byte[] buffer) throws IOException {
        FileOutputStream fileoutputstream = null;
        try {
            fileoutputstream = new FileOutputStream(newFile);
            int n;
            while ((n = zipinputstream.read(buffer)) > -1) {
                fileoutputstream.write(buffer, 0, n);
            }
        } finally {
            if (fileoutputstream != null) {
                fileoutputstream.close();
            }
        }
    }

    private File saveFile(java.io.File tempFile) throws IOException {
        return saveFileOrDir(tempFile, false);
    }

    private File saveDir(java.io.File tempFile) throws IOException {
        return saveFileOrDir(tempFile, true);
    }

    private File saveFileOrDir(java.io.File ioFile, boolean isDir) throws IOException {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session) em.getDelegate();

        String fileName = ioFile.getName();
        String dirName = ioFile.getParent();

        log.debug("saveFileOrDir name: " + ioFile.getName());
        log.debug("saveFileOrDir parent: " + ioFile.getParent());

        File file = (File)session.createQuery("from File where name = :name and directory = :directory")
                .setParameter("name", fileName)
                .setParameter("directory", dirName)
                .uniqueResult();

        if (file == null) {
            // file does not exist in DB; we are not overwriting a file
            file = new File();
            file.setName(fileName);
            file.setDirectory(dirName);
            file.setIsDirectory(isDir);

            session.save(file);
        } // else: file exists in DB and thus on disk; we have chosen to overwrite the file on disk
        
        return file;
    }

    public Resolution check() {
        if (uploaderStatus != null) {
            java.io.File dirFile = new java.io.File(getUploadDirectory());
            if (!dirFile.exists())
                dirFile.mkdir();

            java.io.File tempFile = new java.io.File(dirFile, uploaderStatus.getFname());
            log.debug(tempFile.getAbsolutePath());
            log.debug(tempFile.getPath());

            log.debug("check fpath: ");
            if (uploaderStatus.getFpath() != null) {
                log.debug(uploaderStatus.getFpath());
            }

            // TODO: check ook op andere dingen, size enzo. Dit blijft natuurlijk alleen maar een convenience check. Heeft niets met safety te maken.
            Map resultMap = new HashMap();

            // TODO: exists check is niet goed
            if (tempFile.exists()) {
                uploaderStatus.setErrtype("exists");
            } if (isZipFile(tempFile) && zipFileToDirFile(tempFile, new java.io.File(getUploadDirectory())).exists()) {
                uploaderStatus.setErrtype("exists");
            } else {
                uploaderStatus.setErrtype("none");
            }
            resultMap.put("0", uploaderStatus);
            return new JSONResolution(resultMap);
        }
        return new JSONResolution(false);
    }
    
    private boolean isZipFile(String fileName) {
        return fileName.toLowerCase().endsWith(ZIP_EXT);
    }

    private boolean isZipFile(java.io.File file) {
        return file.getName().toLowerCase().endsWith(ZIP_EXT);
    }

    private String getZipName(String zipFileName) {
        return zipFileName.substring(0, zipFileName.length() - ZIP_EXT.length());
    }

    private String getZipName(java.io.File zipFile) {
        return zipFile.getName().substring(0, zipFile.getName().length() - ZIP_EXT.length());
    }

    private java.io.File zipFileToDirFile(java.io.File zipFile, java.io.File parent) {
        return new java.io.File(parent, getZipName(zipFile));
    }

    public List<File> getFiles() {
        return files;
    }

    public void setFiles(List<File> files) {
        this.files = files;
    }

    public String getUploadDirectory() {
        return getContext().getServletContext().getInitParameter("uploadDirectory");
    }

    public Long getSelectedFileId() {
        return selectedFileId;
    }

    public void setSelectedFileId(Long selectedFileId) {
        this.selectedFileId = selectedFileId;
    }

    public String getSelectedFileIds() {
        return selectedFileIds;
    }

    public void setSelectedFileIds(String selectedFileIds) {
        this.selectedFileIds = selectedFileIds;
    }

    public Long getDir() {
        return dir;
    }

    public void setDir(Long dir) {
        this.dir = dir;
    }

    public List<File> getDirectories() {
        return directories;
    }

    public void setDirectories(List<File> directories) {
        this.directories = directories;
    }

}
