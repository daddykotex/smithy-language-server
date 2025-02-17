/*
 * Copyright 2020 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *  http://aws.amazon.com/apache2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */

package software.amazon.smithy.lsp;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import org.eclipse.lsp4j.CompletionOptions;
import org.eclipse.lsp4j.InitializeParams;
import org.eclipse.lsp4j.InitializeResult;
import org.eclipse.lsp4j.MessageParams;
import org.eclipse.lsp4j.MessageType;
import org.eclipse.lsp4j.ServerCapabilities;
import org.eclipse.lsp4j.TextDocumentIdentifier;
import org.eclipse.lsp4j.TextDocumentSyncKind;
import org.eclipse.lsp4j.WorkspaceFolder;
import org.eclipse.lsp4j.services.LanguageClient;
import org.eclipse.lsp4j.services.LanguageClientAware;
import org.eclipse.lsp4j.services.LanguageServer;
import org.eclipse.lsp4j.services.TextDocumentService;
import org.eclipse.lsp4j.services.WorkspaceService;
import software.amazon.smithy.lsp.ext.LspLog;
import software.amazon.smithy.lsp.ext.ValidationException;

public class SmithyLanguageServer implements LanguageServer, LanguageClientAware, SmithyProtocolExtensions {

  private Optional<LanguageClient> client = Optional.empty();

  private File workspaceRoot;

  private Optional<SmithyTextDocumentService> tds = Optional.empty();

  @Override
  public CompletableFuture<Object> shutdown() {
    return Utils.completableFuture(new Object());
  }

  private void loadSmithyBuild(File root) throws ValidationException, FileNotFoundException {
    this.tds.ifPresent(tds -> tds.createProject(root));
  }

  @Override
  public CompletableFuture<InitializeResult> initialize(InitializeParams params) {
    LspLog.println(params.getWorkspaceFolders());
    if (params.getRootUri() != null) {
      try {
        workspaceRoot = new File(new URI(params.getRootUri()));
        loadSmithyBuild(workspaceRoot);
      } catch (Exception e) {
        LspLog.println("Failure trying to load extensions from workspace root: " + workspaceRoot.getAbsolutePath());
        LspLog.println(e.toString());
      }
    } else {
      LspLog.println("Workspace root was null");
    }

    // TODO: This will break on multi-root workspaces
    for (WorkspaceFolder ws : params.getWorkspaceFolders()) {
      try {
        File root = new File(new URI(ws.getUri()));
        LspLog.setWorkspaceFolder(root);
        loadSmithyBuild(root);
      } catch (Exception e) {
        LspLog.println("Error when loading workspace folder " + ws.toString() + ": " + e.toString());
      }
    }

    ServerCapabilities capabilities = new ServerCapabilities();
    capabilities.setTextDocumentSync(TextDocumentSyncKind.Full);
    capabilities.setCodeActionProvider(false);
    capabilities.setDefinitionProvider(true);
    capabilities.setDeclarationProvider(true);
    capabilities.setCompletionProvider(new CompletionOptions(true, null));
    capabilities.setHoverProvider(false);

    return Utils.completableFuture(new InitializeResult(capabilities));
  }

  @Override
  public void exit() {
    System.exit(0);

  }

  @Override
  public WorkspaceService getWorkspaceService() {
    return new SmithyWorkspaceService(this.tds);
  }

  @Override
  public TextDocumentService getTextDocumentService() {
    File temp = null;
    try {
      temp = Files.createTempDirectory("smithy-lsp").toFile();
      LspLog.println("Created a temporary folder for file contents " + temp);
      temp.deleteOnExit();
    } catch (IOException e) {
      LspLog.println("Failed to create a temporary folder " + e);
    }
    SmithyTextDocumentService local = new SmithyTextDocumentService(this.client, temp);
    tds = Optional.of(local);
    return local;
  }

  @Override
  public void connect(LanguageClient client) {
    tds.ifPresent(tds -> tds.setClient(client));
    client.showMessage(new MessageParams(MessageType.Info, "Hello from smithy-language-server !"));
  }

  @Override
  public CompletableFuture<String> jarFileContents(TextDocumentIdentifier documentUri) {
    String uri = documentUri.getUri();

    try {
      LspLog.println("Trying to resolve " + uri);
      List<String> lines = Utils.jarFileContents(uri);
      String contents = lines.stream().collect(Collectors.joining(System.lineSeparator()));
      return CompletableFuture.completedFuture(contents);
    } catch (IOException e) {
      LspLog.println("Failed to resolve " + uri + " error: " + e.toString());
      CompletableFuture<String> future = new CompletableFuture<String>();
      future.completeExceptionally(e);
      return future;
    }
  }

}
