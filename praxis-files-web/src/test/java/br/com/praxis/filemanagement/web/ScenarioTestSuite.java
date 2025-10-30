package br.com.praxis.filemanagement.web;

import br.com.praxis.filemanagement.web.controller.FileControllerMimeTypeTest;
import br.com.praxis.filemanagement.web.error.GlobalExceptionHandlerTest;
import br.com.praxis.filemanagement.web.filter.RateLimitingFilterTest;
import br.com.praxis.filemanagement.web.monitoring.MonitoringControllerTest;
import org.junit.platform.suite.api.SelectClasses;
import org.junit.platform.suite.api.Suite;

@Suite
@SelectClasses({
    FileControllerMimeTypeTest.class,
    GlobalExceptionHandlerTest.class,
    RateLimitingFilterTest.class,
    MonitoringControllerTest.class
})
public class ScenarioTestSuite {
}
