/*
  UserControllerTest.java

  Created by John Boyer on Sep 6, 2017
  Copyright (c) 2017 Rodax Software, Inc.

  This Source Code Form is subject to the terms of the Mozilla Public
  License, v. 2.0. If a copy of the MPL was not distributed with this
  file, You can obtain one at http://mozilla.org/MPL/2.0/.
*/
package com.rodaxsoft.todo.test.rest;

import static com.rodaxsoft.todo.security.SecurityConstants.HEADER_STRING;
import static com.rodaxsoft.todo.test.TaskTestUtils.getProfileFromJson;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rodaxsoft.todo.TaskApplication;
import com.rodaxsoft.todo.data.DynamoDBUserMapper;
import com.rodaxsoft.todo.domain.Profile;
import com.rodaxsoft.todo.domain.UserItem;
import com.rodaxsoft.todo.security.JWTToken;
import com.rodaxsoft.todo.test.TaskTestUtils;
import com.rodaxsoft.todo.test.TestBeanProvider;


/**
 * UserControllerTest class
 */
@RunWith(SpringRunner.class)
@SpringBootTest(
  webEnvironment = WebEnvironment.RANDOM_PORT,
  classes = TaskApplication.class)
@AutoConfigureMockMvc
public class UserControllerTest implements TestBeanProvider {
	private static final String ACCESS_TOKENS_PATH = "/access-tokens";
	
	private static final String REFRESH_ACCESS_TOKENS_PATH = ACCESS_TOKENS_PATH + "/refresh";

	private static final String USERS_PATH = "/users";

	@Autowired
	private DynamoDBUserMapper applicationUserRepository;
	
	@Autowired
	private BCryptPasswordEncoder bCryptPasswordEncoder;
	
	@Autowired
	private MockMvc mvc;

	private UserItem savedUser;
	
	@Override
	public DynamoDBUserMapper getApplicationUserRepository() {
		return applicationUserRepository;
	}
	
	@Override
	public BCryptPasswordEncoder getBCryptPasswordEncoder() {
		return bCryptPasswordEncoder;
	}
	
	@Before
	public void setUp() {
		savedUser = TaskTestUtils.saveMockApplicationUser(this);
	}
	
	@After
	public void tearDown() {
		if(savedUser != null) {
			applicationUserRepository.deleteUser(savedUser);
		}
	}
	
	/**
	 * @param savedUser
	 * @return
	 */
	private Map<String, String> getCredentialsMap(UserItem mockUser) {
		Map<String, String> credentials = new HashMap<>();
		credentials.put("email", mockUser.getEmail());
		credentials.put("password", mockUser.getPassword());
		return credentials;
	}

	@Test
	public void testGetProfile() {
		
		MvcResult result;
		Map<String, String> credentials = getCredentialsMap(savedUser);
		try {
			//First login
			String json = new ObjectMapper().writeValueAsString(credentials);
			result = mvc.perform(post(ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json))
				   .andExpect(status().isOk())
				   .andExpect(jsonPath("$.jwt", notNullValue()))
			       .andExpect(jsonPath("$.refresh_token", notNullValue())).andReturn();
			
			MockHttpServletResponse response = result.getResponse();			
			JWTToken token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);
			
			//Fetch profile
			result = mvc.perform(get("/me")
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json)
				     .header(HEADER_STRING, token.getAccessToken()))
				   .andExpect(status().isOk())
				   .andDo(print())
				   .andReturn();
			
			response = result.getResponse();
			Profile profile = getProfileFromJson(response.getContentAsString());
			Assert.assertNotNull(profile);
			Assert.assertNotNull(profile.getEmail());
			Assert.assertNotNull(profile.getName());
			
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
		
	}


	@Test
	public void testLoginUser() {
		
		Map<String, String> credentials = getCredentialsMap(savedUser);
		
		try {
			String json = new ObjectMapper().writeValueAsString(credentials);
			MvcResult result = mvc.perform(post(ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json))
				   .andExpect(status().isOk())
				   .andExpect(jsonPath("$.jwt", notNullValue()))
			       .andExpect(jsonPath("$.refresh_token", notNullValue()))
			       .andDo(print())
			       .andReturn();
			
			MockHttpServletResponse response = result.getResponse();			
			JWTToken token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);
			
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
		
	}


	@Test
	public void testLogout() {
		
		Map<String, String> credentials = getCredentialsMap(savedUser);

		try {

			//Perform login first
			String json = new ObjectMapper().writeValueAsString(credentials);
			MvcResult result = mvc.perform(post(ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json))
				   .andExpect(status().isOk())
				   .andExpect(jsonPath("$.jwt", notNullValue()))
			       .andExpect(jsonPath("$.refresh_token", notNullValue())).andReturn();
			
			MockHttpServletResponse response = result.getResponse();
			
			JWTToken token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);
			
			//Then perform logout
			mvc.perform(delete(ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(token.toRefreshTokenJson())
				     .header(HEADER_STRING, token.getAccessToken()))
				   .andExpect(status().isOk())
				   .andDo(print())
				   .andReturn();
			
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}

	@Test
	public void testRefreshAccessToken() {

		Map<String, String> credentials = getCredentialsMap(savedUser);
		
		try {

			//Perform login first
			String json = new ObjectMapper().writeValueAsString(credentials);
			MvcResult result = mvc.perform(post(ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json))
				   .andExpect(status().isOk())
				   .andExpect(jsonPath("$.jwt", notNullValue()))
			       .andExpect(jsonPath("$.refresh_token", notNullValue())).andReturn();
			
			MockHttpServletResponse response = result.getResponse();
			
			JWTToken token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);
			
			//Then refresh access token
			result = mvc.perform(post(REFRESH_ACCESS_TOKENS_PATH)
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(token.toRefreshTokenJson())
				     .header(HEADER_STRING, token.getAccessToken()))
				   .andExpect(status().isOk())
				   .andExpect(jsonPath("$.jwt", notNullValue()))
				   .andDo(print())
				   .andReturn();
			
			response = result.getResponse();
			token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);
			
			//Fetch the profile with new access token
			result = mvc.perform(get("/me")
				     .contentType(MediaType.APPLICATION_JSON)
				     .content(json)
				     .header(HEADER_STRING, token.getAccessToken()))
				   .andExpect(status().isOk())
				   .andReturn();
			
			response = result.getResponse();
			Profile profile = getProfileFromJson(response.getContentAsString());
			Assert.assertNotNull(profile);
			Assert.assertNotNull(profile.getEmail());
			Assert.assertNotNull(profile.getName());

			
			
		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}
	
	@Test
	public void testSignupUser() {	
		try {
			
			//Delete the new user
			applicationUserRepository.deleteUser(savedUser);
			
			String json = TaskTestUtils.createMockApplicationUserJson();
			MvcResult result = mvc.perform(post(USERS_PATH)
					     .contentType(MediaType.APPLICATION_JSON)
					     .content(json))
					   .andExpect(status().isOk())
					   .andExpect(jsonPath("$.jwt", notNullValue()))
			           .andExpect(jsonPath("$.refresh_token", notNullValue()))
			           .andDo(print())
			           .andReturn();
			
			
			MockHttpServletResponse response = result.getResponse();			
			JWTToken token = JWTToken.fromJson(response.getContentAsString());
			Assert.assertNotNull(token);

		} catch (Exception e) {
			Assert.fail(e.getMessage());
		}
	}
	
}
