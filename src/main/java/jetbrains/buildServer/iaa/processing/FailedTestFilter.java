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

package jetbrains.buildServer.iaa.processing;

import java.util.List;
import java.util.stream.Collectors;
import jetbrains.buildServer.iaa.common.FailedBuildInfo;
import jetbrains.buildServer.iaa.utils.CustomParameters;
import jetbrains.buildServer.iaa.utils.FlakyTestDetector;
import jetbrains.buildServer.iaa.utils.InvestigationsManager;
import jetbrains.buildServer.serverSide.SBuild;
import jetbrains.buildServer.serverSide.SProject;
import jetbrains.buildServer.serverSide.STest;
import jetbrains.buildServer.serverSide.STestRun;
import org.jetbrains.annotations.NotNull;
import org.springframework.stereotype.Component;

@Component
public class FailedTestFilter implements ContextFilter {

  private final InvestigationsManager myInvestigationsManager;
  private final FlakyTestDetector myFlakyTestDetector;

  public FailedTestFilter(@NotNull FlakyTestDetector flakyTestDetector,
                          @NotNull final InvestigationsManager investigationsManager) {
    myFlakyTestDetector = flakyTestDetector;
    myInvestigationsManager = investigationsManager;
  }

  @Override
  public HeuristicContext apply(final HeuristicContext heuristicContext) {
    FailedBuildInfo failedBuildInfo = heuristicContext.getFailedBuildInfo();
    SBuild sBuild = heuristicContext.getSBuild();
    SProject sProject = heuristicContext.getSProject();
    Integer threshold = CustomParameters.getMaxTestsPerBuildThreshold(sBuild);

    List<STestRun> filteredTestRuns = heuristicContext.getSTestRuns().stream()
                                                      .filter(failedBuildInfo::checkNotProcessed)
                                                      .filter(testRun -> isApplicable(sProject, sBuild, testRun))
                                                      .limit(threshold - failedBuildInfo.processed)
                                                      .collect(Collectors.toList());

    failedBuildInfo.addProcessedTestRuns(heuristicContext.getSTestRuns());
    failedBuildInfo.processed += filteredTestRuns.size();

    return new HeuristicContext(failedBuildInfo, heuristicContext.getBuildProblems(), filteredTestRuns);
  }

  private boolean isApplicable(@NotNull final SProject project,
                               @NotNull final SBuild sBuild,
                               @NotNull final STestRun testRun) {
    final STest test = testRun.getTest();

    return !testRun.isMuted() &&
           !testRun.isFixed() &&
           testRun.isNewFailure() &&
           !myInvestigationsManager.checkUnderInvestigation(project, sBuild, test) &&
           !myFlakyTestDetector.isFlaky(test.getTestNameId());
  }
}