package com.friendsleague;

import org.springframework.boot.SpringApplication;

public class TestFriendsleagueApplication {

	public static void main(String[] args) {
		SpringApplication.from(FriendsleagueApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}
