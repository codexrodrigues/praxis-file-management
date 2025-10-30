package br.com.praxis.filemanagement.core;

import br.com.praxis.filemanagement.core.services.NameConflictPolicyTest;
import br.com.praxis.filemanagement.core.services.VirusScanningServiceTest;
import br.com.praxis.filemanagement.core.validation.InputValidationServiceTest;
import br.com.praxis.filemanagement.core.services.FileUploadApiTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    FileUploadApiTest.class,
    InputValidationServiceTest.class,
    VirusScanningServiceTest.class,
    NameConflictPolicyTest.class
})
public class ScenarioTestSuite {
}
