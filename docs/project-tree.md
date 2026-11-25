# Árbol completo de archivos publicables del proyecto

Inventario revisado para el primer commit. `.cache/`, `target/`, `.vscode/`, secretos y datos locales no se incorporan. Ver [pre-publication-audit.md](pre-publication-audit.md).

```text
Foodly-Community-Service/
|-- .mvn/
|   |-- wrapper/
|   |   `-- maven-wrapper.properties
|   `-- maven.config
|-- docs/
|   |-- architecture.md
|   |-- contracts.md
|   |-- messaging.md
|   |-- persistence.md
|   |-- pre-publication-audit.md
|   |-- project-tree.md
|   |-- security.md
|   `-- verification.md
|-- src/
|   |-- main/
|   |   |-- java/
|   |   |   `-- com/
|   |   |       `-- foodly/
|   |   |           `-- community/
|   |   |               |-- application/
|   |   |               |   |-- commandhandlers/
|   |   |               |   |   |-- AddFavoriteCommandHandler.java
|   |   |               |   |   |-- CreateReviewCommandHandler.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- RemoveFavoriteCommandHandler.java
|   |   |               |   |-- commands/
|   |   |               |   |   |-- AddFavoriteCommand.java
|   |   |               |   |   |-- CreateReviewCommand.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- RemoveFavoriteCommand.java
|   |   |               |   |-- errors/
|   |   |               |   |   |-- AuthenticationRequiredException.java
|   |   |               |   |   `-- FavoriteNotFoundException.java
|   |   |               |   |-- ports/
|   |   |               |   |   |-- CurrentExplorer.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- ReviewIdGenerator.java
|   |   |               |   |-- queries/
|   |   |               |   |   |-- GetFavoritesByExplorerQuery.java
|   |   |               |   |   |-- GetReviewsByHuariqueQuery.java
|   |   |               |   |   |-- GetReviewSummaryByHuariqueQuery.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- ReviewSummary.java
|   |   |               |   `-- queryhandlers/
|   |   |               |       |-- GetFavoritesByExplorerQueryHandler.java
|   |   |               |       |-- GetReviewsByHuariqueQueryHandler.java
|   |   |               |       |-- GetReviewSummaryByHuariqueQueryHandler.java
|   |   |               |       `-- package-info.java
|   |   |               |-- domain/
|   |   |               |   |-- events/
|   |   |               |   |   `-- package-info.java
|   |   |               |   |-- model/
|   |   |               |   |   |-- DomainIdentifiers.java
|   |   |               |   |   |-- Favorite.java
|   |   |               |   |   |-- FavoriteId.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   |-- Rating.java
|   |   |               |   |   |-- Review.java
|   |   |               |   |   `-- ReviewComment.java
|   |   |               |   |-- repository/
|   |   |               |   |   |-- AggregateRepository.java
|   |   |               |   |   |-- DuplicateFavoriteException.java
|   |   |               |   |   |-- IFavoriteRepository.java
|   |   |               |   |   |-- IReviewRepository.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- RepositoryAccessException.java
|   |   |               |   `-- service/
|   |   |               |       |-- package-info.java
|   |   |               |       `-- ReputationService.java
|   |   |               |-- infrastructure/
|   |   |               |   |-- config/
|   |   |               |   |   |-- BusinessEventsConfiguration.java
|   |   |               |   |   |-- CommunityApplicationConfiguration.java
|   |   |               |   |   |-- MongoPersistenceConfiguration.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- RuntimeAdaptersConfiguration.java
|   |   |               |   |-- identity/
|   |   |               |   |   `-- UuidReviewIdGenerator.java
|   |   |               |   |-- messaging/
|   |   |               |   |   |-- business/
|   |   |               |   |   |   |-- BusinessEvent.java
|   |   |               |   |   |   |-- BusinessEventListener.java
|   |   |               |   |   |   |-- BusinessEventParser.java
|   |   |               |   |   |   |-- BusinessEventsProperties.java
|   |   |               |   |   |   |-- BusinessInboxDocument.java
|   |   |               |   |   |   |-- BusinessTopology.java
|   |   |               |   |   |   |-- InvalidBusinessEventException.java
|   |   |               |   |   |   |-- KnownHuariqueDocument.java
|   |   |               |   |   |   `-- MongoBusinessEventStore.java
|   |   |               |   |   `-- package-info.java
|   |   |               |   |-- persistence/
|   |   |               |   |   |-- mongo/
|   |   |               |   |   |   |-- document/
|   |   |               |   |   |   |   |-- FavoriteDocument.java
|   |   |               |   |   |   |   `-- ReviewDocument.java
|   |   |               |   |   |   |-- mapper/
|   |   |               |   |   |   |   |-- FavoriteDocumentMapper.java
|   |   |               |   |   |   |   `-- ReviewDocumentMapper.java
|   |   |               |   |   |   |-- FavoriteRepository.java
|   |   |               |   |   |   `-- ReviewRepository.java
|   |   |               |   |   `-- package-info.java
|   |   |               |   `-- security/
|   |   |               |       |-- IamJwtConfiguration.java
|   |   |               |       |-- IamJwtDecoderFactory.java
|   |   |               |       |-- IamJwtProperties.java
|   |   |               |       |-- package-info.java
|   |   |               |       |-- SecurityConfiguration.java
|   |   |               |       `-- SpringSecurityCurrentExplorer.java
|   |   |               |-- interfaces/
|   |   |               |   |-- dto/
|   |   |               |   |   |-- ApiErrorResource.java
|   |   |               |   |   |-- CreateReviewResource.java
|   |   |               |   |   |-- FavoriteResource.java
|   |   |               |   |   |-- IntegerRatingDeserializer.java
|   |   |               |   |   |-- package-info.java
|   |   |               |   |   `-- ReviewResource.java
|   |   |               |   `-- rest/
|   |   |               |       |-- FavoriteController.java
|   |   |               |       |-- package-info.java
|   |   |               |       |-- RestExceptionHandler.java
|   |   |               |       `-- ReviewController.java
|   |   |               `-- CommunityServiceApplication.java
|   |   `-- resources/
|   |       `-- application.properties
|   `-- test/
|       `-- java/
|           `-- com/
|               `-- foodly/
|                   `-- community/
|                       |-- application/
|                       |   |-- commandhandlers/
|                       |   |   |-- AddFavoriteCommandHandlerTest.java
|                       |   |   |-- CreateReviewCommandHandlerTest.java
|                       |   |   `-- RemoveFavoriteCommandHandlerTest.java
|                       |   |-- ports/
|                       |   |   `-- CurrentExplorerTest.java
|                       |   `-- queryhandlers/
|                       |       |-- GetFavoritesByExplorerQueryHandlerTest.java
|                       |       |-- GetReviewsByHuariqueQueryHandlerTest.java
|                       |       `-- GetReviewSummaryByHuariqueQueryHandlerTest.java
|                       |-- domain/
|                       |   |-- model/
|                       |   |   |-- FavoriteTest.java
|                       |   |   |-- RatingTest.java
|                       |   |   |-- ReviewCommentTest.java
|                       |   |   `-- ReviewTest.java
|                       |   |-- repository/
|                       |   |   |-- FavoriteRepositoryContract.java
|                       |   |   `-- InMemoryFavoriteRepositoryContractTest.java
|                       |   `-- service/
|                       |       `-- ReputationServiceTest.java
|                       |-- infrastructure/
|                       |   |-- config/
|                       |   |   |-- BusinessEventsConfigurationTest.java
|                       |   |   |-- MongoPersistenceConfigurationTest.java
|                       |   |   `-- RuntimeAdaptersConfigurationTest.java
|                       |   |-- persistence/
|                       |   |   `-- mongo/
|                       |   |       |-- BusinessEventConsumerTest.java
|                       |   |       |-- BusinessEventsIntegrationTest.java
|                       |   |       |-- BusinessEventTestData.java
|                       |   |       |-- CommunityJwtMongoIntegrationTest.java
|                       |   |       |-- DocumentMapperTest.java
|                       |   |       |-- FavoriteRepositoryTest.java
|                       |   |       |-- MongoPersistenceIntegrationTest.java
|                       |   |       |-- ReviewRepositoryTest.java
|                       |   |       |-- TemporaryMongoServer.java
|                       |   |       `-- TemporaryRabbitServer.java
|                       |   `-- security/
|                       |       |-- IamJwtConfigurationTest.java
|                       |       |-- IamJwtDecoderFactoryTest.java
|                       |       |-- JwtTestTokens.java
|                       |       `-- SpringSecurityCurrentExplorerTest.java
|                       |-- interfaces/
|                       |   |-- dto/
|                       |   |   `-- ResourceMappingTest.java
|                       |   `-- rest/
|                       |       |-- CommunityRestSecurityTests.java
|                       |       |-- FavoriteControllerTest.java
|                       |       |-- RestIdentityBoundaryTest.java
|                       |       |-- RestMvcTestSupport.java
|                       |       `-- ReviewControllerTest.java
|                       `-- CommunityServiceApplicationTests.java
|-- .env.example
|-- .gitattributes
|-- .gitignore
|-- mvnw
|-- mvnw.cmd
|-- pom.xml
`-- README.md
```

La evidencia local en `.cache/audit-verify.log` y `target/surefire-reports/` permanece privada. El resultado resumido se publica en `verification.md`.
