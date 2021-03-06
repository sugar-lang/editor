package org.sugarj.builder;

import java.util.Set;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.spoofax.jsglr.shared.BadTokenException;
import org.sugarj.cleardep.BuildUnit;
import org.sugarj.common.FileCommands;
import org.sugarj.common.path.Path;
import org.sugarj.common.path.RelativePath;
import org.sugarj.driver.Result;
import org.sugarj.util.ProcessingListener;

/**
 * @author seba
 */
public class MarkingProcessingListener extends ProcessingListener {
  
  private IProject project;
  
  public MarkingProcessingListener(IProject project) {
    this.project = project;
  }

  private IResource getResource(Path sourceFile) throws JavaModelException {
    if (!sourceFile.getAbsolutePath().startsWith(project.getLocation().toString()))
      return null;
    
    try {
      for (IPackageFragmentRoot frag : JavaCore.create(project).getAllPackageFragmentRoots())
        if (frag.getKind() == IPackageFragmentRoot.K_SOURCE) {
          IResource resource = project.findMember(frag.getPath().makeRelativeTo(project.getFullPath()).append(FileCommands.tryGetRelativePath(sourceFile)));
          if (resource != null)
            return resource;
        }
    } catch (JavaModelException e) { }
    return null;
  }
  
  @Override
  public void processingStarts(Set<RelativePath> sourceFiles) {
    for (RelativePath sourceFile : sourceFiles) {
      try {
        IResource resource = getResource(sourceFile);
        if (resource != null)
          resource.deleteMarkers(IMarker.MARKER, true, IResource.DEPTH_INFINITE);
      } catch (CoreException e) {
      }
    }
  }

  @Override
  public void processingDone(BuildUnit<Result> result) {
    try {
      for (Path sourceFile : result.getSourceArtifacts()) {
        IResource resource = getResource(sourceFile);
        if (resource == null)
          continue;
        
        for (String error : result.getBuildResult().getCollectedErrors()) {
          IMarker marker = resource.createMarker(IMarker.PROBLEM);
          marker.setAttribute(IMarker.MESSAGE, "compilation failed: " + error);
        }
        
        for (BadTokenException error : result.getBuildResult().getParseErrors()) {
          IMarker marker = resource.createMarker(IMarker.PROBLEM);
          marker.setAttribute(IMarker.MESSAGE, "parsing failed: " + error.getLocalizedMessage());
        }
      }
    } catch (CoreException e) {
    }
  }

}
