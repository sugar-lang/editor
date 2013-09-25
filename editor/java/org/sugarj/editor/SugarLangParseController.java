package org.sugarj.editor;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IPath;
import org.eclipse.imp.model.ISourceProject;
import org.eclipse.imp.parser.IMessageHandler;
import org.eclipse.imp.parser.IParseController;
import org.strategoxt.imp.runtime.dynamicloading.BadDescriptorException;
import org.strategoxt.imp.runtime.dynamicloading.Descriptor;
import org.strategoxt.imp.runtime.dynamicloading.DescriptorFactory;
import org.strategoxt.imp.runtime.parser.JSGLRI;
import org.strategoxt.imp.runtime.parser.SGLRParseController;
import org.sugarj.BaseLanguageRegistry;
import org.sugarj.common.Environment;
import org.sugarj.common.FileCommands;
import org.sugarj.common.StringCommands;

public class SugarLangParseController extends SugarLangParseControllerGenerated {
    private static Descriptor descriptor;

    private SugarLangParser sugarjParser;
    private Environment environment;
    
    @Override
    public IParseController getWrapped() {
      initDescriptor();
      IParseController result = super.getWrapped();
      
      if (result instanceof SGLRParseController) {
        JSGLRI parser = ((SGLRParseController) result).getParser();
        if (!(parser instanceof SugarLangParser)) {
          sugarjParser = new SugarLangParser(parser);
          sugarjParser.setEnvironment(environment);        
          ((SGLRParseController) result).setParser(sugarjParser);
        }
      }
      
      return result;
    }
    
    public static synchronized Descriptor getDescriptor() { 
      initDescriptor();
      return descriptor;
    }

    public static synchronized Descriptor initDescriptor() {
      try {
        if (descriptor == null) {
          descriptor = new SugarLangDescriptor(createDescriptorWithRegisteredExtensions());
          descriptor.setAttachmentProvider(SugarLangParseControllerGenerated.class);
          setDescriptor(descriptor);
          // TODO: Optimize - generated parse controller also registers and reinitializes the descriptor...
          org.strategoxt.imp.runtime.Environment.registerDescriptor(descriptor.getLanguage(), descriptor);
        }
        return descriptor;
      } catch (BadDescriptorException e) {
        org.strategoxt.imp.runtime.Environment.logException("Bad descriptor for " + LANGUAGE + " plugin", e);
        throw new RuntimeException("Bad descriptor for " + LANGUAGE + " plugin", e);
      }
    }
    
    /*
     * next four declarations are copied from SugarJParseControllerGenerated, except for the call to imposeRegisteredExtensions.
     */
    private static final String TABLE = "/include/" + LANGUAGE + ".tbl";
    private static final String DESCRIPTOR = "/include/" + LANGUAGE + ".packed.esv";
    private static String getPluginLocation() { 
      return SugarLangParseController.class.getProtectionDomain().getCodeSource().getLocation().getFile();
    }
    private static Descriptor createDescriptorWithRegisteredExtensions() {
      try
      { 
        InputStream descriptorStream = SugarLangParseControllerGenerated.class.getResourceAsStream(DESCRIPTOR);
        InputStream table = SugarLangParseControllerGenerated.class.getResourceAsStream(TABLE);
        boolean filesystem = false;
        if(descriptorStream == null && new File("./" + DESCRIPTOR).exists())
        { 
          descriptorStream = new FileInputStream("./" + DESCRIPTOR);
          filesystem = true;
        }
        if(table == null && new File("./" + TABLE).exists())
        { 
          table = new FileInputStream("./" + TABLE);
          filesystem = true;
        }
        if(descriptorStream == null)
          throw new BadDescriptorException("Could not load descriptor file from " + DESCRIPTOR + " (not found in plugin: " + getPluginLocation() + ")");
        if(table == null)
          throw new BadDescriptorException("Could not load parse table from " + TABLE + " (not found in plugin: " + getPluginLocation() + ")");
        
        descriptorStream = imposeRegisteredExtensions(descriptorStream);
        
        Descriptor descriptor = DescriptorFactory.load(descriptorStream, table, filesystem ? org.eclipse.core.runtime.Path.fromPortableString("./") : null);
        descriptor.setAttachmentProvider(SugarLangParseControllerGenerated.class);
        return descriptor;
      }
      catch(BadDescriptorException exc)
      { 
        org.strategoxt.imp.runtime.Environment.logException("Bad descriptor for " + LANGUAGE + " plugin", exc);
        throw new RuntimeException("Bad descriptor for " + LANGUAGE + " plugin", exc);
      }
      catch(IOException exc)
      { 
        org.strategoxt.imp.runtime.Environment.logException("I/O problem loading descriptor for " + LANGUAGE + " plugin", exc);
        throw new RuntimeException("I/O problem loading descriptor for " + LANGUAGE + " plugin", exc);
      }
    }

    @Override
    public void initialize(IPath filePath, ISourceProject project,
        IMessageHandler handler) {
      super.initialize(filePath, project, handler);
      
      if (project != null)
        initializeEnvironment(project.getRawProject());
    }
    
    private void initializeEnvironment(IProject project) {
      if (project != null)
        environment = SugarLangProjectEnvironment.makeProjectEnvironment(project);
      
      if (sugarjParser != null)
        sugarjParser.setEnvironment(environment);
    }
    
    private static InputStream imposeRegisteredExtensions(InputStream descriptorStream) {
      String in;
      try {
        in = FileCommands.readStreamAsString(descriptorStream);
      } catch (IOException e) {
        return descriptorStream;
      }
      List<String> exts = BaseLanguageRegistry.getInstance().getRegisteredFileExtensions();
      for (int i = 0; i < exts.size(); i++)
        exts.set(i, "\"" + exts.get(i) + "\"");
      String extsString = StringCommands.printListSeparated(exts, ",");
      String out = in.replace("Extensions(Values([", "Extensions(Values([" + extsString);
      return new ByteArrayInputStream(out.getBytes());
    }

  }