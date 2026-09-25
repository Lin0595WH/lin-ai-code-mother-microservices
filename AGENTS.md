# Repository Guidelines

## Project Structure & Module Organization

This is a Java 21 Maven reactor; build from the repository root. Shared layers are `lin-ai-code-common`, `lin-ai-code-model`, and `lin-ai-code-client`. Runtime services are `lin-ai-code-user` (8124), `lin-ai-code-app` (8125), and `lin-ai-code-screenshot` (8127); `lin-ai-code-ai` contains LangChain4j prompts, tools, and guardrails. Each module keeps Java sources in `src/main/java`, resources in `src/main/resources`, and tests in `src/test`. Generated applications and deployments go under `tmp/code_output` and `tmp/code_deploy`.

## Build, Test, and Development Commands

Run commands from the root with JDK 21 and Maven 3.9+:

```bash
mvn clean verify                         # compile all modules and run tests
mvn test                                 # run the complete test suite
mvn -pl lin-ai-code-app -am test         # test app and dependencies
mvn -pl lin-ai-code-user -am package -DskipTests  # package one service
mvn clean install -DskipTests            # install artifacts for local startup
```

Start MySQL, Redis, and Nacos before launching the Spring Boot main classes in the service modules. Chrome/Chromium is also required by the screenshot service.

## Coding Style & Naming Conventions

Use UTF-8, four-space indentation, and standard Java naming: `PascalCase` types, `camelCase` methods and fields, and `UPPER_SNAKE_CASE` constants. Keep packages under `com.lin...`, follow the controller/service/mapper split, and prefer Lombok constructor injection. No formatter or linter is configured, so match surrounding code.

## Testing Guidelines

Tests use JUnit 5 through `spring-boot-starter-test`, with Mockito for unit isolation. Place tests beside the module they exercise, name classes `*Test`, and use descriptive `should...` methods; use parameterized tests for input matrices. Run the narrowest Maven command first, then `mvn clean verify`. Mock MySQL, Redis, AI, COS, and browser integrations unless an integration test requires them.

## Configuration and Security

Copy or create ignored `application-local.yml` files for model, database, Redis, Nacos, and COS settings. Never commit API keys, passwords, session credentials, generated `tmp/` output, or local IDE files; use environment-variable overrides where possible.

## Commit and Pull Request Guidelines

Follow the history's Conventional Commit style, such as `feat(app): ...`, `fix(ai): ...`, or `config: ...`; keep subjects concise and scoped. A pull request should explain the behavior change, affected modules, validation commands, configuration or schema changes, and include API examples or screenshots for user-facing changes. Link the relevant issue when one exists.
