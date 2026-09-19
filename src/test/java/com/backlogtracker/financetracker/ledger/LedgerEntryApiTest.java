package com.backlogtracker.financetracker.ledger;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.backlogtracker.commons.group.repository.GroupRepository;
import com.backlogtracker.commons.security.JwtService;
import com.backlogtracker.commons.user.domain.AccountStatus;
import com.backlogtracker.commons.user.domain.Role;
import com.backlogtracker.commons.user.domain.User;
import com.backlogtracker.commons.user.repository.UserRepository;
import com.backlogtracker.support.AuthTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;

/** tg-12: Finance Tracker had no HTTP-level proof that a caller who isn't a member of the
 *  group is rejected, only ever exercised at the service layer where requireMember's own
 *  unit tests already live -- this closes the gap at the controller boundary. */
@SpringBootTest
@AutoConfigureMockMvc
class LedgerEntryApiTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired UserRepository users;
    @Autowired JwtService jwt;
    @Autowired GroupRepository groups;

    private String groupId;
    private String outsiderToken;

    @BeforeEach
    void setUp() throws Exception {
        users.findByEmailIgnoreCase("ledgerouter@ft.test").ifPresent(users::delete);
        groups.deleteAll();
        String token = AuthTestSupport.devToken(mvc, mapper);
        groupId = AuthTestSupport.createGroup(mvc, mapper, token, "Finance Co " + System.nanoTime());

        User outsider = users.save(User.builder().name("Outsider").email("ledgerouter@ft.test")
                .passwordHash("x").role(Role.USER).status(AccountStatus.ACTIVE).handle("ledgerouter").build());
        outsiderToken = jwt.issue(outsider);
    }

    private MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String t) {
        return b.header("Authorization", "Bearer " + t);
    }

    @Test
    void nonMemberIsForbiddenFromListingLedgerEntries() throws Exception {
        mvc.perform(auth(get("/api/financetracker/groups/" + groupId + "/ledger"), outsiderToken))
                .andExpect(status().isForbidden());
    }
}
