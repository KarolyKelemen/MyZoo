package com.myzoodata.controllers;

import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HomeController {

	@RequestMapping("/")
	String home() {
		return "Hello World!";
	}
}