package services

/**
  * Abstraction for the redirect behaviour in Profile pages
  * This exists because once you are in any profile page it is possible
  * to switch between them using javascript tabs, bypassing a normal
  * redirection.
  *
  * Both AuthenticatedActions and the ProfileForms template can use
  * this service to make redirects work:
  * a) Normally via play routing
  * b) Replacing the javascript tab links in the template
  */


import com.gu.identity.model.User
import play.api.mvc.{ControllerComponents, RequestHeader}

import scala.concurrent.{ExecutionContext, Future}

sealed abstract class ProfileRedirect(val url: String) {
  def isAllowedFrom(url: String): Boolean
}

case object RedirectToEmailValidationFromEmailPrefsOrConsentJourney extends ProfileRedirect("/verify-email") {
  override def isAllowedFrom(url: String): Boolean = (url startsWith "/email-prefs") || (url startsWith "/consents")
}

case object RedirectToConsentsFromEmailPrefs extends ProfileRedirect("/consents/staywithus") {
  override def isAllowedFrom(url: String): Boolean = url contains "email-prefs"
}

case object RedirectToNewsletterConsentsFromEmailPrefs extends ProfileRedirect("/consents/newsletters") {
  override def isAllowedFrom(url: String): Boolean = url contains "email-prefs"
}

case object NoRedirect extends ProfileRedirect("") {
  override def isAllowedFrom(url: String): Boolean = false
}

/**
  * Where users should be redirected to depends on two factors:
  *   1. has user validated the email address
  *   2. has user re-permissioned before
  */
class ProfileRedirectService(
     newsletterService: NewsletterService,
     idRequestParser: IdRequestParser,
     controllerComponents: ControllerComponents) {

  private implicit lazy val ec: ExecutionContext = controllerComponents.executionContext

  def toConsentsRedirect[A](user: User, request: RequestHeader): ProfileRedirect =
    if (user.statusFields.isUserEmailValidated) NoRedirect
    else RedirectToEmailValidationFromEmailPrefsOrConsentJourney


  def toProfileRedirect[A](user: User, request: RequestHeader): ProfileRedirect = {

    val userHasRepermissioned = user.statusFields.hasRepermissioned.contains(true)
    val userEmailValidated = user.statusFields.isUserEmailValidated

    (userEmailValidated, userHasRepermissioned) match {
      case (false, _) => RedirectToEmailValidationFromEmailPrefsOrConsentJourney
      case (true, false) => RedirectToConsentsFromEmailPrefs
      case (true, true) => NoRedirect
    }
  }

}
