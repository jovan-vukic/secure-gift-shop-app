package com.zuehlke.securesoftwaredevelopment.controller;

import com.zuehlke.securesoftwaredevelopment.config.AuditLogger;
import com.zuehlke.securesoftwaredevelopment.config.SecurityUtil;
import com.zuehlke.securesoftwaredevelopment.domain.Person;
import com.zuehlke.securesoftwaredevelopment.domain.Role;
import com.zuehlke.securesoftwaredevelopment.domain.User;
import com.zuehlke.securesoftwaredevelopment.repository.PersonRepository;
import com.zuehlke.securesoftwaredevelopment.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpSession;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

@Controller

public class PersonsController {

    private static final Logger LOG = LoggerFactory.getLogger(PersonsController.class);
    private static final AuditLogger auditLogger = AuditLogger.getAuditLogger(PersonRepository.class);

    private final PersonRepository personRepository;
    private final UserRepository userRepository;

    public PersonsController(PersonRepository personRepository, UserRepository userRepository) {
        this.personRepository = personRepository;
        this.userRepository = userRepository;
    }

    @GetMapping("/persons/{id}")
    public String person(@PathVariable int id, Model model, HttpSession session) {
        if (!SecurityUtil.hasPermission("VIEW_PERSON") && SecurityUtil.getCurrentUser().getId() != id) {
            auditLogger.audit("Unauthorized attempt to perform action 'VIEW_PERSON' by USER_ID = " + String.valueOf(SecurityUtil.getCurrentUser().getId()));
            throw new AccessDeniedException("Forbidden");
        }
        model.addAttribute("CSRF_TOKEN", session.getAttribute("CSRF_TOKEN"));
        model.addAttribute("person", personRepository.get("" + id));
        return "person";
    }

    @GetMapping("/myprofile")
    @PreAuthorize("hasAuthority('VIEW_MY_PROFILE')")
    public String self(Model model, Authentication authentication, HttpSession session) {
        User user = (User) authentication.getPrincipal();
        model.addAttribute("CSRF_TOKEN", session.getAttribute("CSRF_TOKEN"));
        model.addAttribute("person", personRepository.get("" + user.getId()));
        return "person";
    }

    @DeleteMapping("/persons/{id}")
    @PreAuthorize("hasAuthority('UPDATE_PERSON') or hasAuthority('UPDATE_SELF')")
    public ResponseEntity<Void> person(@PathVariable int id) {
        // For 'MANAGER' and 'BUYER'
        if (!SecurityUtil.hasPermission("UPDATE_PERSON") && SecurityUtil.hasPermission("UPDATE_SELF")) {
            User currentUser = SecurityUtil.getCurrentUser();
            if (currentUser.getId() != id) {
                auditLogger.audit("Unauthorized attempt to perform action 'DELETE_PERSON' by USER_ID = " + String.valueOf(SecurityUtil.getCurrentUser().getId()));
                throw new AccessDeniedException("Forbidden");
            }
        }

        personRepository.delete(id);
        userRepository.delete(id);

        return ResponseEntity.noContent().build();
    }

    @PostMapping("/update-person")
    @PreAuthorize("hasAuthority('UPDATE_PERSON') or hasAuthority('UPDATE_SELF')")
    public String updatePerson(
            Person person,
            HttpSession session,
            @RequestParam("csrfToken") String csrfToken
    ) throws AccessDeniedException {
        String csrf = session.getAttribute("CSRF_TOKEN").toString();
        if (!csrf.equals(csrfToken)) {
            auditLogger.audit("Unauthorized attempt to perform action, without valid CSRF token.");
            throw new AccessDeniedException("Forbidden");
        }

        // For 'MANAGER' and 'BUYER'
        if (!SecurityUtil.hasPermission("UPDATE_PERSON") && SecurityUtil.hasPermission("UPDATE_SELF")) {
            User currentUser = SecurityUtil.getCurrentUser();
            if (currentUser.getId() != Integer.parseInt(person.getId())) {
                auditLogger.audit("Unauthorized attempt to perform action 'UPDATE_PERSON' by USER_ID = " + String.valueOf(SecurityUtil.getCurrentUser().getId()));
                throw new AccessDeniedException("Forbidden");
            }
        }
        personRepository.update(person);
        return "redirect:/persons/" + person.getId();
    }

    @GetMapping("/persons")
    @PreAuthorize("hasAuthority('VIEW_PERSONS_LIST')")
    public String persons(Model model) {
        model.addAttribute("persons", personRepository.getAll());
        return "persons";
    }

    @GetMapping(value = "/persons/search", produces = "application/json")
    @ResponseBody
    @PreAuthorize("hasAuthority('VIEW_PERSONS_LIST')")
    public List<Person> searchPersons(@RequestParam String searchTerm) throws SQLException {
        return personRepository.search(searchTerm);
    }
}
