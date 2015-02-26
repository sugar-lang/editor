package org.sugarj.builder;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IResourceDelta;
import org.eclipse.core.resources.IResourceVisitor;
import org.eclipse.core.resources.IncrementalProjectBuilder;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.imp.editor.UniversalEditor;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IEditorReference;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.part.FileEditorInput;
import org.sugarj.AbstractBaseLanguage;
import org.sugarj.BaseLanguageRegistry;
import org.sugarj.cleardep.build.BuildManager;
import org.sugarj.cleardep.build.RequiredBuilderFailed;
import org.sugarj.common.CommandExecution;
import org.sugarj.common.FileCommands;
import org.sugarj.common.Log;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.driver.Driver;
import org.sugarj.driver.DriverBuildRequirement;
import org.sugarj.driver.DriverInput;
import org.sugarj.driver.Environment;
import org.sugarj.driver.ModuleSystemCommands;
import org.sugarj.editor.SugarLangConsole;
import org.sugarj.editor.SugarLangProjectEnvironment;
import org.sugarj.util.ProcessingListener;

/**
 * updates editors to show newly built results
 * 
 * @author Sebastian Erdweg <seba at informatik uni-marburg de>
 */
public class Builder extends IncrementalProjectBuilder {

//  private static Map<IProject, ILock> buildLocks = new HashMap<IProject, ILock>();
//  
//  private synchronized static ILock getLock(IProject project) {
//    ILock lock = buildLocks.get(project);
//    if (lock != null)
//      return lock;
//    lock = Job.getJobManager().newLock();
//    buildLocks.put(project, lock);
//    return lock;
//  }
  
  protected IProject[] build(int kind, @SuppressWarnings("rawtypes") Map args, IProgressMonitor monitor) {
    if (kind == IncrementalProjectBuilder.FULL_BUILD) {
      fullBuild(monitor);
    } else {
      IResourceDelta delta = getDelta(getProject());
      if (delta == null) {
        fullBuild(monitor);
      } else {
        incrementalBuild(delta, monitor);
      }
    }
    return null;
  }

  protected void clean(IProgressMonitor monitor) throws CoreException {
    File f = getProject().getLocation().append(JavaCore.create(getProject()).getOutputLocation().makeRelativeTo(getProject().getFullPath())).toFile();
    Environment environment = SugarLangProjectEnvironment.makeProjectEnvironment(getProject());
    try {
      if (f.exists())
        FileCommands.delete(new AbsolutePath(f.getPath()));
      if (FileCommands.exists(environment.getCacheDir()))
        FileCommands.delete(environment.getCacheDir());
    } catch (IOException e) {
      e.printStackTrace();
    }

  }

  private void incrementalBuild(IResourceDelta delta, IProgressMonitor monitor) {
    boolean rebuild = true;

    if (rebuild)
      fullBuild(monitor);
  }

  private void fullBuild(IProgressMonitor monitor) {
    final BaseLanguageRegistry languageReg = BaseLanguageRegistry.getInstance();
    final Map<RelativePath, IResource> resources = new HashMap<>();

    final Environment environment = SugarLangProjectEnvironment.makeProjectEnvironment(getProject());

    try {
      getProject().accept(new IResourceVisitor() {

        @Override
        public boolean visit(IResource resource) throws CoreException {
          Path root = new AbsolutePath(getProject().getLocation().makeAbsolute().toString());
          IPath relPath = resource.getFullPath().makeRelativeTo(getProject().getFullPath());
          if (!relPath.isEmpty() && 
              (environment.getBin().equals(new RelativePath(root, relPath.toString())) 
               || environment.getIncludePath().contains(new RelativePath(root, relPath.toString()))))
            return false;

          if (languageReg.isRegistered(resource.getFileExtension())) {
            String path = getProject().getLocation().makeAbsolute() + "/" + relPath;
            final RelativePath sourceFile = ModuleSystemCommands.locateSourceFile(path.toString(), environment.getSourcePath());

            if (sourceFile == null) {
              // org.strategoxt.imp.runtime.Environment.logWarning("cannot locate source file for ressource " + resource.getFullPath());
              return false;
            }

            resources.put(sourceFile, resource);
          }
          return true;
        }
      });
    } catch (CoreException e) {
      e.printStackTrace();
    }

    build(environment, monitor, resources, "project " + getProject().getName());
  }

  private void build(final Environment environment, IProgressMonitor monitor, final Map<RelativePath, IResource> resources, String what) {
    final BaseLanguageRegistry languageReg = BaseLanguageRegistry.getInstance();

    CommandExecution.SILENT_EXECUTION = false;
    CommandExecution.SUB_SILENT_EXECUTION = false;
    CommandExecution.FULL_COMMAND_LINE = true;

    Log.out = SugarLangConsole.getOutputPrintStream();
    Log.err = SugarLangConsole.getErrorPrintStream();
    SugarLangConsole.activateConsoleOnce();

    Job buildJob = new Job("Build " + what) {
      @Override
      protected IStatus run(IProgressMonitor monitor) {
        ProcessingListener marker = new MarkingProcessingListener(getProject());
        try {
          Driver.addProcessingDoneListener(marker);
          // getLock(getProject()).acquire();
  
          BuildManager manager = new BuildManager();
          
          for (RelativePath sourceFile : resources.keySet()) {
            AbstractBaseLanguage baselang = languageReg.getBaseLanguage(FileCommands.getExtension(sourceFile));
            try {
              DriverInput input = new DriverInput(environment, baselang, sourceFile, monitor);
              manager.require(new DriverBuildRequirement(input));
            } catch (Exception e) {
              if (e instanceof RequiredBuilderFailed && e.getCause() instanceof InterruptedException)
                throw (InterruptedException) e.getCause();
              
              e.printStackTrace();
              try {
                IMarker m = resources.get(sourceFile).createMarker(IMarker.PROBLEM);
                m.setAttribute(IMarker.MESSAGE, "compilation failed: " + e.getMessage());
              } catch (CoreException ce) {
              }
            }
  
            updateUI(sourceFile);
          }
        } catch (InterruptedException e) {
          monitor.setCanceled(true);
          monitor.done();
          return Status.CANCEL_STATUS;
        } finally {
          // getLock(getProject()).release();
          Driver.removeProcessingDoneListener(marker);
          monitor.done();
        }
        return Status.OK_STATUS;
      }
    };
    buildJob.setRule(getProject());
    buildJob.schedule();
  }

  protected static void updateUI(RelativePath sourceFile) {
    IWorkbenchWindow[] workbenchWindows = PlatformUI.getWorkbench().getWorkbenchWindows();
    for (IWorkbenchWindow workbenchWindow : workbenchWindows)
      for (IWorkbenchPage page : workbenchWindow.getPages())
        for (IEditorReference editorRef : page.getEditorReferences()) {
          IEditorPart editor = editorRef.getEditor(false);
          if (editor != null && editor instanceof UniversalEditor && editor.getEditorInput() instanceof FileEditorInput && ((UniversalEditor) editor).fParserScheduler != null && !Thread.currentThread().isInterrupted()) {
            IFile file = ((FileEditorInput) editor.getEditorInput()).getFile();
            if (file.getLocation().toString().equals(sourceFile.toString()))
              ((UniversalEditor) editor).fParserScheduler.schedule();
          }
        }
  }
}
