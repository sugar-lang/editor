package org.sugarj.builder;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
import org.sugarj.cleardep.BuildSchedule;
import org.sugarj.cleardep.BuildScheduleBuilder;
import org.sugarj.cleardep.CompilationUnit;
import org.sugarj.cleardep.Mode;
import org.sugarj.cleardep.BuildSchedule.Task;
import org.sugarj.cleardep.stamp.Stamp;
import org.sugarj.common.CommandExecution;
import org.sugarj.common.FileCommands;
import org.sugarj.common.Log;
import org.sugarj.common.path.AbsolutePath;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.driver.Driver;
import org.sugarj.driver.DriverParameters;
import org.sugarj.driver.Environment;
import org.sugarj.driver.ModuleSystemCommands;
import org.sugarj.driver.Result;
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
    Environment environment = SugarLangProjectEnvironment.makeProjectEnvironment(getProject(), false);
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

    final Environment environment = SugarLangProjectEnvironment.makeProjectEnvironment(getProject(), false);

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
    final Map<RelativePath, Stamp> editedSourceFiles = Collections.emptyMap();

    final Mode<Result> mode = environment.<Result> getMode();

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
        Driver.addProcessingDoneListener(marker);
        // getLock(getProject()).acquire();

        Set<CompilationUnit> allUnitsToCompile = new HashSet<>();
        for (RelativePath sourceFile : resources.keySet()) {
          RelativePath dep = new RelativePath(environment.getBin(), FileCommands.dropExtension(sourceFile.getRelativePath()) + ".dep");
          try {
            Result res = Result.read(dep);
            if (res == null) {
              res = Result.create(environment.getStamper(), mode, null, dep);
              res.addSourceArtifact(sourceFile);
            }
            allUnitsToCompile.add(res);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        }

        BuildScheduleBuilder scheduleBuilder = new BuildScheduleBuilder(allUnitsToCompile, BuildSchedule.ScheduleMode.REBUILD_INCONSISTENT);
        List<Task> schedule = scheduleBuilder.createBuildSchedule(editedSourceFiles).getOrderedSchedule();

        try {
          for (Task task : schedule) {
            if (!task.needsToBeBuild(editedSourceFiles))
              continue;

            Set<CompilationUnit> units = task.getUnitsToCompile();
            for (CompilationUnit unit : units) {
              if (unit.getSynthesizer() == null)
                for (RelativePath sourceFile : unit.getSourceArtifacts()) {
                  if (Thread.currentThread().isInterrupted())
                    throw new InterruptedException();
                  monitor.beginTask("compile " + sourceFile.getRelativePath(), IProgressMonitor.UNKNOWN);

                  AbstractBaseLanguage baselang = languageReg.getBaseLanguage(FileCommands.getExtension(sourceFile));
                  try {
                    Driver.run(DriverParameters.create(environment, baselang, sourceFile, monitor));
                  } catch (InterruptedException e) {
                    throw e;
                  } catch (Exception e) {
                    e.printStackTrace();
                    try {
                      IMarker m = resources.get(sourceFile).createMarker(IMarker.PROBLEM);
                      m.setAttribute(IMarker.MESSAGE, "compilation failed: " + e.getMessage());
                    } catch (CoreException ce) {
                    }
                  }

                  updateUI(sourceFile);
                }
            }
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
