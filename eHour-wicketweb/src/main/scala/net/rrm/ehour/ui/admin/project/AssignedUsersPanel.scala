package net.rrm.ehour.ui.admin.project

import net.rrm.ehour.ui.common.panel.AbstractBasePanel
import net.rrm.ehour.ui.common.border.GreyRoundedBorder
import org.apache.wicket.model.{PropertyModel, Model, IModel}
import org.apache.wicket.spring.injection.annot.SpringBean
import net.rrm.ehour.project.service.{ProjectAssignmentManagementService, ProjectAssignmentService}
import net.rrm.ehour.domain.{ProjectAssignment, Project}
import net.rrm.ehour.util._
import org.apache.wicket.markup.html.list.{ListItem, ListView}
import org.apache.wicket.ajax.markup.html.form.AjaxCheckBox
import org.apache.wicket.ajax.AjaxRequestTarget
import java.lang.Boolean
import org.apache.wicket.markup.head.{IHeaderResponse, CssHeaderItem}
import org.apache.wicket.request.resource.CssResourceReference
import net.rrm.ehour.user.service.UserService
import net.rrm.ehour.ui.common.wicket._
import org.apache.wicket.markup.html.panel.Fragment
import org.apache.wicket.markup.html.form.{Form, CheckBox, TextField}
import net.rrm.ehour.ui.common.panel.datepicker.LocalizedDatePicker
import java.util.Date
import org.apache.wicket.markup.html.WebMarkupContainer
import org.apache.wicket.AttributeModifier

class AssignedUsersPanel(id: String, model: IModel[ProjectAdminBackingBean]) extends AbstractBasePanel[ProjectAdminBackingBean](id, model) {

  val Self = this

  val Css = new CssResourceReference(classOf[AssignedUsersPanel], "projectAdmin.css")

  @SpringBean
  protected var assignmentService: ProjectAssignmentService = _

  @SpringBean
  protected var assignmentManagementService: ProjectAssignmentManagementService = _

  @SpringBean
  protected var userService: UserService = _

  override def onInitialize() {
    super.onInitialize()

    val border = new GreyRoundedBorder("border")
    addOrReplace(border)

    val project = getPanelModelObject.getProject

    val assignments = fetchProjectAssignments(project)

    val container = new Container("assignmentContainer")
    border.addOrReplace(container)
    container.addOrReplace(createAssignmentListView(assignments))

    border.add(new AjaxCheckBox("toggleAll", new Model[Boolean]()) {
      override def onUpdate(target: AjaxRequestTarget) {
        val assignments = if (getModelObject) {
          (fetchUsers ++ fetchProjectAssignments(getPanelModelObject.getProject)).sortWith((a, b) => a.getUser.compareTo(b.getUser) < 0)
        } else {
          fetchProjectAssignments(getPanelModelObject.getProject)
        }

        val view = createAssignmentListView(assignments)
        container.addOrReplace(view)
        target.add(container)
      }
    })
  }

  import WicketDSL._

  def createAssignmentListView(assignments: List[ProjectAssignment]): ListView[ProjectAssignment] = {
    new ListView[ProjectAssignment]("assignments", toJava(assignments)) {
      setOutputMarkupId(true)

      override def populateItem(item: ListItem[ProjectAssignment]) {
        val itemModel = item.getModel

        def createNameLabel = new AlwaysOnLabel("name", new PropertyModel(itemModel, "user.fullName"))

        def createEditFragment: Fragment = {
          def closeEditMode(target: AjaxRequestTarget) {
            val replacement = createShowFragment
            item.addOrReplace(replacement)
            target.add(replacement)
          }

          val fragment = new Fragment("container", "inputRow", Self)
          fragment.setOutputMarkupId(true)

          val form = new Form[Unit]("editForm")
          fragment.add(form)

          form.add(new CheckBox("active", new PropertyModel[Boolean](itemModel, "active")))

          form.add(createNameLabel)

          val dateStart = new LocalizedDatePicker("startDate", new PropertyModel[Date](itemModel, "dateStart"))
          form.add(dateStart)

          val dateEnd = new LocalizedDatePicker("endDate", new PropertyModel[Date](itemModel, "dateEnd"))
          form.add(dateEnd)

          form.add(new TextField("rate", new PropertyModel[Float](itemModel, "hourlyRate")))

          val submitButton = new WebMarkupContainer("submit")
          submitButton.add(ajaxSubmit(form, {
            (form, target) =>
              assignmentManagementService.updateProjectAssignment(itemModel.getObject)
              closeEditMode(target)
          }))

          form.add(submitButton)

          val cancelButton = new WebMarkupContainer("cancel")
          cancelButton.add(ajaxClick({ target => closeEditMode(target)}))
          form.add(cancelButton)

          fragment
        }

        def createShowFragment: Fragment = {
          val container = new Fragment("container", "displayRow", Self)
          container.setOutputMarkupId(true)

          val activeAssignment = new WebMarkupContainer("activeAssignment")
          activeAssignment.add(AttributeModifier.append("class", if (itemModel.getObject.isActive) "ui-icon-bullet" else "ui-icon-radio-off"))
          container.add(activeAssignment)

          container.add(createNameLabel)
          container.add(new DateLabel("startDate", new PropertyModel(itemModel, "dateStart")))
          container.add(new DateLabel("endDate", new PropertyModel(itemModel, "dateEnd")))
          container.add(new AlwaysOnLabel("rate", new PropertyModel(itemModel, "hourlyRate")))

          container.add(ajaxClick({
            target => {
              val replacement = createEditFragment
              item.addOrReplace(replacement)
              target.add(replacement)
            }
          }))

          container
        }

        val container = createShowFragment
        item.add(container)
      }
    }
  }


  def fetchUsers = {
    val project = getPanelModelObject.getProject

    val users = toScala(userService.getActiveUsers)
    users.map(new ProjectAssignment(_, project))
  }

  def fetchProjectAssignments(project: Project): List[ProjectAssignment] = {
    if (project.getProjectId == null) {
      List()
    } else {
      toScala(assignmentService.getProjectAssignments(project))
    }
  }

  override def renderHead(response: IHeaderResponse) {
    response.render(CssHeaderItem.forReference(Css))
  }
}
