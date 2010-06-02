/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package nl.b3p.datastorelinker.gui.stripes;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.persistence.EntityManager;
import net.sourceforge.stripes.action.DefaultHandler;
import net.sourceforge.stripes.action.ForwardResolution;
import net.sourceforge.stripes.action.Resolution;
import net.sourceforge.stripes.util.Log;
import nl.b3p.commons.jpa.JpaUtilServlet;
import nl.b3p.commons.stripes.Transactional;
import nl.b3p.datastorelinker.entity.Actions;
import nl.b3p.datastorelinker.entity.Inout;
import nl.b3p.datastorelinker.util.Mappable;
import nl.b3p.geotools.data.linker.DataStoreLinker;
import org.hibernate.Session;

/**
 *
 * @author Erik van de Pol
 */
public class ProcessAction extends DefaultAction {

    private final static Log log = Log.getInstance(ProcessAction.class);
    
    private final static String JSP = "/pages/main/process/overview.jsp";
    private final static String LIST_JSP = "/pages/main/process/list.jsp";
    private final static String CREATE_JSP = "/pages/main/process/create.jsp";
    private final static String EXECUTE_JSP = "/pages/main/process/execute.jsp";
    
    private List<Process> processes;
    private Long selectedProcessId;
    
    private List<Inout> inputs;
    private Long selectedInputId;

    private List<Inout> inputsFile;
    private List<Inout> inputsDB;
    
    private List<Inout> outputs;
    private Long selectedOutputId;
    
    private Long actionsId;

    public Resolution list() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        // moet even wat hulp krijgen om die order by's goed te krijgen
        // (te maken met de dot-notation voor joins die niet werkt zoals ik denk dat ie werkt.).
        processes = session.createQuery("from Process order by name").list();

        return new ForwardResolution(LIST_JSP);
    }

    @DefaultHandler
    public Resolution overview() {
        list();
        return new ForwardResolution(JSP);
    }

    public Resolution create() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        inputs = session.createQuery("from Inout where typeId = 1").list();
        //inputsFile = session.createQuery("from Inout where typeId = 1 and datatypeId = 2").list();
        //inputsDB = session.createQuery("from Inout where typeId = 1 and datatypeId = 1").list();
        outputs = session.createQuery("from Inout where typeId = 2").list();

        return new ForwardResolution(CREATE_JSP);
        //return new ForwardResolution(InputAction.class, "createProcess");
    }

    @Transactional
    public Resolution createComplete() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        Inout input = (Inout)session.get(Inout.class, selectedInputId);
        Inout output = (Inout)session.get(Inout.class, selectedOutputId);

        nl.b3p.datastorelinker.entity.Process process;
        if (selectedProcessId == null)
            process = new nl.b3p.datastorelinker.entity.Process();
        else
            process = (nl.b3p.datastorelinker.entity.Process)
                    session.get(nl.b3p.datastorelinker.entity.Process.class, selectedProcessId);
        
        // TODO: custom name:
        process.setName(input.getName() + " -> " + output.getName());
        process.setInputId(input);
        process.setOutputId(output);

        // TODO: actionsId erbij

        if (selectedProcessId == null)
            selectedProcessId = (Long)session.save(process);
        //else // automatic saveOrUpdate
            //session.saveOrUpdate(process);
        
        return list();
    }

    public Resolution update() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        nl.b3p.datastorelinker.entity.Process process = (nl.b3p.datastorelinker.entity.Process)
                session.get(nl.b3p.datastorelinker.entity.Process.class, selectedProcessId);

        selectedInputId = process.getInputId().getId();
        selectedOutputId = process.getOutputId().getId();
        // TODO: add actions id(s)

        return create();
    }

    @Transactional
    public Resolution delete() {
        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        session.delete(session.get(nl.b3p.datastorelinker.entity.Process.class, selectedProcessId));
        
        return list();
    }

    public Resolution execute() throws Exception {
        log.debug("Executing process with id: " + selectedProcessId);

        EntityManager em = JpaUtilServlet.getThreadEntityManager();
        Session session = (Session)em.getDelegate();

        nl.b3p.datastorelinker.entity.Process process = (nl.b3p.datastorelinker.entity.Process)
                session.get(nl.b3p.datastorelinker.entity.Process.class, selectedProcessId);

        Map<String, Object> inputMap = createInputMap(process.getInputId());
        Map<String, Object> actionsOutputMap = createActionsOutputMap(process.getOutputId(), null);
        Properties properties = createProperties(process.getInputId());

        log.debug("input:\n" + inputMap);
        log.debug("actionlistMap:\n" + actionsOutputMap);
        log.debug("properties:\n" + properties);
        
        try {
            String result = DataStoreLinker.process(inputMap, actionsOutputMap, properties);
            log.debug(result);
        } catch(Exception e) {
            log.error(e.getMessage());
        }
        
        return new ForwardResolution(EXECUTE_JSP);
    }

    private Map<String, Object> createInputMap(Inout input) {
        Mappable inputMappable = null;

        if (input.getDatabaseId() != null)
            inputMappable = input.getDatabaseId();
        else if (input.getFileId() != null)
            inputMappable = input.getFileId();

        return inputMappable.toMap();
    }

    private Map<String, Object> createActionsOutputMap(Inout output, List<Actions> actionsList) {
        Integer actionNr = 1;
        /*
        for (Actions action : actionsList) {
            //...
            actionNr++;
        }
        */

        Map<String, Object> outputMap = output.getDatabaseId().toMap();

        Map<String, Object> defaultActionSettingsMap = new HashMap<String, Object>();
        defaultActionSettingsMap.put("params", outputMap);
        // TODO: variabel maken:
        defaultActionSettingsMap.put("drop", true);

        Map<String, Object> defaultActionMap = new HashMap<String, Object>();
        defaultActionMap.put("settings", defaultActionSettingsMap);
        defaultActionMap.put("type", "ActionCombo_GeometrySplitter_Writer");

        Map<String, Object> actionlistMap = new HashMap<String, Object>();
        actionlistMap.put(actionNr.toString(), defaultActionMap);

        return actionlistMap;
    }

    private Properties createProperties(Inout input) {
        Properties properties = new Properties();
        
        properties.setProperty("read.typename", input.getTableName() == null ? "edam-volendam" : input.getTableName());

        return properties;
    }

    //TODO: test: is output DB PostGIS (itt alleen Postgres)?

    public List<Process> getProcesses() {
        return processes;
    }

    public void setProcesses(List<Process> processes) {
        this.processes = processes;
    }

    public List<Inout> getInputs() {
        return inputs;
    }

    public void setInputs(List<Inout> inputs) {
        this.inputs = inputs;
    }

    public List<Inout> getOutputs() {
        return outputs;
    }

    public void setOutputs(List<Inout> outputs) {
        this.outputs = outputs;
    }

    public List<Inout> getInputsFile() {
        return inputsFile;
    }

    public void setInputsFile(List<Inout> inputsFile) {
        this.inputsFile = inputsFile;
    }

    public List<Inout> getInputsDB() {
        return inputsDB;
    }

    public void setInputsDB(List<Inout> inputsDB) {
        this.inputsDB = inputsDB;
    }

    public Long getActionsId() {
        return actionsId;
    }

    public void setActionsId(Long actionsId) {
        this.actionsId = actionsId;
    }

    public Long getSelectedInputId() {
        return selectedInputId;
    }

    public void setSelectedInputId(Long selectedInputId) {
        this.selectedInputId = selectedInputId;
    }

    public Long getSelectedOutputId() {
        return selectedOutputId;
    }

    public void setSelectedOutputId(Long selectedOutputId) {
        this.selectedOutputId = selectedOutputId;
    }

    public Long getSelectedProcessId() {
        return selectedProcessId;
    }

    public void setSelectedProcessId(Long selectedProcessId) {
        this.selectedProcessId = selectedProcessId;
    }
}
