<%-- 
    Document   : list
    Created on : 12-mei-2010, 13:24:18
    Author     : Erik van de Pol
--%>
<%@include file="/WEB-INF/jsp/commons/taglibs.jsp" %>

<%@page contentType="text/html" pageEncoding="UTF-8"%>

<script type="text/javascript" class="ui-layout-ignore">
    $(document).ready(function() {
        selectFirstRadioInputIfPresentAndNoneSelected($("#processesList input:radio"));
        $("#processesList").buttonset();
        $("#processesList img[title]").qtip({
            content: false,
            position: {
                corner: {
                    tooltip: "topRight",
                    target: "bottomLeft"
                }
            },
            style: {
                "font-size": 12,
                //width: "200px",
                width: {
                    max: 700
                },
                border: {
                    width: 2,
                    radius: 8
                },
                name: "cream",
                tip: true
            }
        });
    });
    
</script>

<div id="processesList">
    <stripes:form partial="true" action="/">
        <c:forEach var="process" items="${actionBean.processes}" varStatus="status">
            <c:choose>
                <c:when test="${not empty actionBean.selectedProcessId and process.id == actionBean.selectedProcessId}">
                    <input type="radio" id="process${process.id}" name="selectedProcessId" value="${process.id}" class="required" checked="checked"/>
                    <script type="text/javascript" class="ui-layout-ignore">
                        $(document).ready(function() {
                            $("#processesList").parent().scrollTo(
                                $("#process${process.id}"),
                                defaultScrollToDuration,
                                defaultScrollToOptions
                            );
                        });
                    </script>
                </c:when>
                <c:otherwise>
                    <input type="radio" id="process${process.id}" name="selectedProcessId" value="${process.id}" class="required"/>
                </c:otherwise>
            </c:choose>

            <stripes:label for="process${process.id}" title="${process.remarks}">
                <span class="process-status-image">
                    <c:if test="${not empty process.schedule}">
                        <img src="<stripes:url value="/images/clock_48.gif"/>"
                             title="<fmt:message key="process.scheduled"/>"
                             alt="process.scheduled" />
                    </c:if>
                    <c:if test="${process.append}">
                        <img src="<stripes:url value="/images/plus.png"/>"
                             title="<fmt:message key="process.append"/>"
                             alt="process.append" />
                    </c:if>
                    <c:if test="${process.linkedProcess != null}">
                        <img src="<stripes:url value="/images/link_go.png"/>"
                             title="<fmt:message key="process.triggeredBy"><fmt:param value="${process.linkedProcess.name}"/></fmt:message>"
                             alt="process.triggeredBy"/>
                    </c:if>
                    <c:choose>
                        <c:when test="${process.processStatus.processStatusType == 'RUNNING'}">
                            <img src="<stripes:url value="/images/spinner.gif"/>"
                                 title="<fmt:message key="process.running"/>"
                                 alt="process.running" />
                        </c:when>
                        <c:when test="${process.processStatus.processStatusType == 'LAST_RUN_OK'}">
                            <img src="<stripes:url value="/images/circle_green.png"/>"
                                 title="<fmt:message key="process.lastRunOk"/>"
                                 alt="process.lastRunOk" />
                        </c:when>
                        <c:when test="${process.processStatus.processStatusType == 'LAST_RUN_OK_WITH_ERRORS'}">
                            <img src="<stripes:url value="/images/circle_groengeel.png"/>"
                                 title="<c:out value="${process.processStatus.message}"/>"
                                 alt="process.lastRunOkWithErrors" />
                        </c:when>
                        <c:when test="${process.processStatus.processStatusType == 'LAST_RUN_FATAL_ERROR'}">
                            <img src="<stripes:url value="/images/circle_red.png"/>"
                                 title="<c:out value="${process.processStatus.message}"/>"
                                 alt="process.lastRunFatalError" />
                        </c:when>
                        <c:when test="${process.processStatus.processStatusType == 'CANCELED_BY_USER'}">
                            <img src="<stripes:url value="/images/circle_blue.png"/>"
                                 title="<fmt:message key="process.canceledByUser"/>"
                                 alt="process.canceledByUser" />
                        </c:when>
                    </c:choose>
                </span>
                <c:out value="${process.name}"/> | <c:out value="${process.userName}"/>
            </stripes:label>

            <%-- Add schedule icon if this process is scheduled >
            <c:if test="${not empty process.schedule}">
                <script type="text/javascript" class="ui-layout-ignore">
                    $(document).ready(function() {
                        $("#process${process.id}").button("option", "icons", {primary: "ui-icon-clock"});
                    });
                </script>
            </c:if--%>

        </c:forEach>
    </stripes:form>
</div>