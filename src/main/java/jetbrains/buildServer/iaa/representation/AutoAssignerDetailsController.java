/*
 * Copyright 2000-2018 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package jetbrains.buildServer.iaa.representation;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import jetbrains.buildServer.controllers.BaseController;
import jetbrains.buildServer.iaa.common.Responsibility;
import jetbrains.buildServer.iaa.utils.AssignerArtifactDao;
import jetbrains.buildServer.iaa.utils.FlakyTestDetector;
import jetbrains.buildServer.iaa.utils.InvestigationsManager;
import jetbrains.buildServer.responsibility.TestNameResponsibilityEntry;
import jetbrains.buildServer.serverSide.*;
import jetbrains.buildServer.serverSide.stat.FirstFailedInFixedInCalculator;
import jetbrains.buildServer.users.User;
import jetbrains.buildServer.web.openapi.PluginDescriptor;
import jetbrains.buildServer.web.openapi.WebControllerManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.springframework.web.servlet.ModelAndView;

import static jetbrains.buildServer.serverSide.BuildStatisticsOptions.ALL_TESTS_NO_DETAILS;

public class AutoAssignerDetailsController extends BaseController {

  private final FirstFailedInFixedInCalculator myStatisticsProvider;
  private final AssignerArtifactDao myAssignerArtifactDao;
  private final String myDynamicTestDetailsExtensionPath;
  private final String myCssPath;
  @NotNull private final InvestigationsManager myInvestigationsManager;
  private final FlakyTestDetector myFlakyTestDetector;

  public AutoAssignerDetailsController(final SBuildServer server,
                                       @NotNull final FirstFailedInFixedInCalculator statisticsProvider,
                                       @NotNull final AssignerArtifactDao assignerArtifactDao,
                                       @NotNull final WebControllerManager controllerManager,
                                       @NotNull final PluginDescriptor descriptor,
                                       @NotNull final FlakyTestDetector flakyTestDetector,
                                       @NotNull final InvestigationsManager investigationsManager) {
    super(server);
    myStatisticsProvider = statisticsProvider;
    myAssignerArtifactDao = assignerArtifactDao;
    myFlakyTestDetector = flakyTestDetector;
    myDynamicTestDetailsExtensionPath = descriptor.getPluginResourcesPath("dynamicTestDetailsExtension.jsp");
    myCssPath = descriptor.getPluginResourcesPath("testDetailsExtension.css");
    myInvestigationsManager = investigationsManager;
    controllerManager.registerController("/autoAssignerController.html", this);
  }

  @Nullable
  @Override
  protected ModelAndView doHandle(@NotNull final HttpServletRequest request,
                                  @NotNull final HttpServletResponse response) {
    final long buildId = Long.parseLong(request.getParameter("buildId"));
    final int testId = Integer.parseInt(request.getParameter("testId"));

    final SBuild build = myServer.findBuildInstanceById(buildId);
    if (build == null) return null;

    STestRun sTestRun = build.getBuildStatistics(ALL_TESTS_NO_DETAILS).findTestByTestRunId(testId);
    if (sTestRun == null) return null;

    if (myFlakyTestDetector.isFlaky(sTestRun.getTest().getTestNameId())) {
      return null;
    }
    final FirstFailedInFixedInCalculator.FFIData ffiData = myStatisticsProvider.calculateFFIData(sTestRun);

    @Nullable SBuild firstFailedBuild = ffiData.getFirstFailedIn();
    Responsibility responsibility = myAssignerArtifactDao.get(firstFailedBuild, sTestRun);

    if (responsibility != null && !isAlreadyAssignedToSameUser(build, sTestRun.getTest(), responsibility.getUser())) {
      final ModelAndView modelAndView = new ModelAndView( myDynamicTestDetailsExtensionPath);
      modelAndView.getModel().put("userId", responsibility.getUser().getId());
      modelAndView.getModel().put("userName", responsibility.getUser().getDescriptiveName());
      String shownDescription = responsibility.getDescription();
      if (firstFailedBuild != null && firstFailedBuild.getBuildId() != buildId && shownDescription.endsWith("build")) {
        shownDescription = shownDescription + " with the first test failure";
      }
      modelAndView.getModel().put("shownDescription", shownDescription);
      modelAndView.getModel().put("investigationDescription", responsibility.getDescription());
      modelAndView.getModel().put("buildId", buildId);
      modelAndView.getModel().put("projectId", build.getProjectId());
      modelAndView.getModel().put("test", sTestRun.getTest());
      modelAndView.getModel().put("myCssPath", request.getContextPath() + myCssPath);
      return modelAndView;
    }

    return null;
  }

  private boolean isAlreadyAssignedToSameUser(SBuild sBuild, STest sTest, User user) {
    SBuildType sBuildType = sBuild.getBuildType();
    if (sBuildType == null) {
      return false;
    }

    SProject sProject = sBuildType.getProject();

    @Nullable
    TestNameResponsibilityEntry investigationEntry = myInvestigationsManager.getInvestigation(sProject, sBuild, sTest);
    if (investigationEntry == null) {
      return false;
    }

    return investigationEntry.getResponsibleUser().getId() == user.getId();
  }
}