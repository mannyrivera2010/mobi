package com.mobi.prolly.record.grpc;

/*-
 * #%L
 * com.mobi.prolly.record.grpc
 * $Id:$
 * $HeadURL:$
 * %%
 * Copyright (C) 2016 - 2026 iNovex Information Systems, Inc.
 * %%
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 * #L%
 */

import com.earasoft.prolly.rdf4j.grpc.versioning.BranchRef;
import com.earasoft.prolly.rdf4j.grpc.versioning.ChangeKind;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitId;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitPair;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitRef;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitResult;
import com.earasoft.prolly.rdf4j.grpc.versioning.CommitSpec;
import com.earasoft.prolly.rdf4j.grpc.versioning.CreateBranchRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.CreateRepoRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.DiffRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.LeaseId;
import com.earasoft.prolly.rdf4j.grpc.versioning.LeaseRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.ProllyVersioningGrpc;
import com.earasoft.prolly.rdf4j.grpc.versioning.Quad;
import com.earasoft.prolly.rdf4j.grpc.versioning.QuadChange;
import com.earasoft.prolly.rdf4j.grpc.versioning.QuadChangeBatch;
import com.earasoft.prolly.rdf4j.grpc.versioning.RepoRef;
import com.earasoft.prolly.rdf4j.grpc.versioning.SailType;
import com.earasoft.prolly.rdf4j.grpc.versioning.ServerInfo;
import com.earasoft.prolly.rdf4j.grpc.versioning.SetBranchHeadRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.SetBranchHeadResult;
import com.earasoft.prolly.rdf4j.grpc.versioning.SnapshotRequest;
import com.earasoft.prolly.rdf4j.grpc.versioning.TermDictionary;
import com.google.protobuf.ByteString;
import com.google.protobuf.Empty;
import com.mobi.prolly.record.api.client.ProllyDiff;
import com.mobi.prolly.record.api.client.ProllyVersioningClient;
import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder;
import io.grpc.stub.MetadataUtils;
import org.eclipse.rdf4j.model.IRI;
import org.eclipse.rdf4j.model.Model;
import org.eclipse.rdf4j.model.Resource;
import org.eclipse.rdf4j.model.Statement;
import org.eclipse.rdf4j.model.Value;
import org.eclipse.rdf4j.model.ValueFactory;
import org.eclipse.rdf4j.model.impl.DynamicModelFactory;
import org.eclipse.rdf4j.model.impl.ValidatingValueFactory;
import org.eclipse.rdf4j.rio.helpers.NTriplesUtil;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Deactivate;
import org.osgi.service.metatype.annotations.AttributeDefinition;
import org.osgi.service.metatype.annotations.Designate;
import org.osgi.service.metatype.annotations.ObjectClassDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * {@link ProllyVersioningClient} over the vendored {@code versioning.proto} gRPC contract — the
 * live half of plan {@code plans/grpc-prolly-versioning-client.md} (Phases 1–2) and the
 * prolly-versioned-rdf-record plan's Phase 5. Configuration-gated: without an
 * {@code endpoint} config this component stays down and the in-memory fake serves; with one, its
 * higher {@code service.ranking} wins the single {@code ProllyVersioningClient} reference.
 *
 * <p>OSGi notes (the grpc-in-Karaf de-risk, grpc plan Step 1): all gRPC/protobuf jars are embedded
 * in this bundle (fat bundle, single classloader) and the channel is built with the shaded
 * {@code NettyChannelBuilder} directly — never {@code ManagedChannelBuilder.forAddress}, whose
 * {@code ServiceLoader} discovery is unreliable under OSGi.
 *
 * <p>Term encoding matches the server's own: standard N-Triples term strings
 * ({@code NTriplesUtil}), the same library both sides.
 */
@Component(
        immediate = true,
        service = ProllyVersioningClient.class,
        configurationPolicy = org.osgi.service.component.annotations.ConfigurationPolicy.REQUIRE,
        property = "service.ranking:Integer=100"
)
@Designate(ocd = GrpcProllyVersioningClient.Config.class)
public class GrpcProllyVersioningClient implements ProllyVersioningClient {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcProllyVersioningClient.class);
    private static final Metadata.Key<String> REPO_HEADER =
            Metadata.Key.of("prolly-repo", Metadata.ASCII_STRING_MARSHALLER);
    private static final Metadata.Key<String> API_KEY_HEADER =
            Metadata.Key.of("x-api-key", Metadata.ASCII_STRING_MARSHALLER);
    private static final int LEASE_TTL_SECONDS = 300;

    @ObjectClassDefinition(name = "Prolly gRPC Versioning Client",
            description = "Connects prolly-backed records to an out-of-process prolly-rdf4j gRPC service")
    public @interface Config {
        @AttributeDefinition(description = "host:port of the prolly gRPC service")
        String endpoint() default "localhost:9090";

        @AttributeDefinition(required = false, description = "x-api-key value; empty = no auth (dev mode)")
        String apiKey() default "";
    }

    private final ValueFactory vf = new ValidatingValueFactory();
    private volatile ManagedChannel channel;
    private volatile String apiKey;

    @Activate
    void activate(Config config) {
        String[] hostPort = config.endpoint().split(":", 2);
        this.apiKey = config.apiKey();
        this.channel = NettyChannelBuilder
                .forAddress(hostPort[0], Integer.parseInt(hostPort[1]))
                .usePlaintext()
                .build();
        try {
            ServerInfo info = lifecycleStub().getServerInfo(Empty.getDefaultInstance());
            LOG.info("Connected to prolly gRPC service at {} — server {} contract {} (capabilities: {})",
                    config.endpoint(), info.getServerVersion(), info.getContractVersion(),
                    info.getCapabilitiesList());
        } catch (Exception e) {
            LOG.warn("Prolly gRPC service at {} not reachable at activation; calls will retry lazily",
                    config.endpoint(), e);
        }
    }

    @Deactivate
    void deactivate() {
        if (channel != null) {
            channel.shutdown();
            try {
                channel.awaitTermination(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    // ── Repo lifecycle ───────────────────────────────────────────────────────

    @Override
    public void createRepo(String repoKey) {
        try {
            lifecycleStub().createRepo(CreateRepoRequest.newBuilder()
                    .setRepo(repoKey)
                    .setDescription("Mobi ProllyVersionedRDFRecord content")
                    .setSailType(SailType.PROLLY)
                    .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                throw new IllegalArgumentException("Repo already exists: " + repoKey, e);
            }
            throw e;
        }
    }

    @Override
    public void deleteRepo(String repoKey) {
        try {
            lifecycleStub().deleteRepo(RepoRef.newBuilder().setRepo(repoKey).build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() != Status.Code.NOT_FOUND) {
                throw e;
            }
        }
    }

    @Override
    public boolean repoExists(String repoKey) {
        try {
            lifecycleStub().getRepo(RepoRef.newBuilder().setRepo(repoKey).build());
            return true;
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return false;
            }
            throw e;
        }
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Override
    public Model snapshot(String repoKey, String commitHash) {
        Iterator<QuadChangeBatch> batches = repoStub(repoKey).snapshot(SnapshotRequest.newBuilder()
                .setTarget(CommitRef.newBuilder().setId(commitId(commitHash)))
                .build());
        Model model = new DynamicModelFactory().createEmptyModel();
        batches.forEachRemaining(batch -> decodeBatch(batch, model::add, st -> {
            throw new IllegalStateException("Snapshot stream must not carry DELETE changes");
        }));
        return model;
    }

    @Override
    public ProllyDiff diff(String repoKey, String fromCommit, String toCommit) {
        Iterator<QuadChangeBatch> batches = repoStub(repoKey).diff(DiffRequest.newBuilder()
                .setFrom(commitId(fromCommit))
                .setTo(commitId(toCommit))
                .build());
        Model additions = new DynamicModelFactory().createEmptyModel();
        Model deletions = new DynamicModelFactory().createEmptyModel();
        batches.forEachRemaining(batch -> decodeBatch(batch, additions::add, deletions::add));
        return new ProllyDiff(additions, deletions);
    }

    @Override
    public Optional<String> getMergeBase(String repoKey, String commitA, String commitB) {
        try {
            CommitId base = repoStub(repoKey).getMergeBase(CommitPair.newBuilder()
                    .setFrom(commitId(commitA))
                    .setTo(commitId(commitB))
                    .build());
            return base.getHash().isEmpty() ? Optional.empty() : Optional.of(hex(base));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    // ── Writes ───────────────────────────────────────────────────────────────

    @Override
    public String commit(String repoKey, Model additions, Model deletions, List<String> parents,
                         String message) {
        CommitRequest.Builder request = CommitRequest.newBuilder();
        CommitSpec.Builder spec = CommitSpec.newBuilder()
                .setAuthor("mobi")
                .setMessage(message == null || message.isBlank() ? "(no message)" : message);
        if (!parents.isEmpty()) {
            request.setExpectedHead(commitId(parents.get(0)));
            parents.stream().skip(1).forEach(p -> spec.addExtraParents(commitId(p)));
        }
        request.setSpec(spec);
        request.setDelta(encodeBatch(additions, deletions));

        CommitResult result = repoStub(repoKey).commit(request.build());
        if (result.getStaleHead()) {
            throw new IllegalStateException("Prolly commit lost the head race in repo " + repoKey
                    + "; server head is " + hex(result.getCurrentHead())
                    + " but expected " + (parents.isEmpty() ? "(root)" : parents.get(0)));
        }
        if (result.getNoOp() && result.getCommit().getHash().isEmpty()) {
            if (parents.isEmpty()) {
                throw new IllegalStateException("Empty root commit elided by the server in repo " + repoKey);
            }
            return parents.get(0);
        }
        return hex(result.getCommit());
    }

    // ── Branches ─────────────────────────────────────────────────────────────

    @Override
    public void createBranch(String repoKey, String branch, String commitHash) {
        try {
            repoStub(repoKey).createBranch(CreateBranchRequest.newBuilder()
                    .setBranch(branch)
                    .setFrom(commitId(commitHash))
                    .build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.ALREADY_EXISTS) {
                throw new IllegalArgumentException("Branch already exists: " + branch, e);
            }
            throw e;
        }
    }

    @Override
    public void deleteBranch(String repoKey, String branch) {
        try {
            repoStub(repoKey).deleteBranch(BranchRef.newBuilder().setBranch(branch).build());
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() != Status.Code.NOT_FOUND) {
                throw e;
            }
        }
    }

    @Override
    public Optional<String> getBranchHead(String repoKey, String branch) {
        try {
            CommitId head = repoStub(repoKey).getBranchHead(BranchRef.newBuilder().setBranch(branch).build());
            return head.getHash().isEmpty() ? Optional.empty() : Optional.of(hex(head));
        } catch (StatusRuntimeException e) {
            if (e.getStatus().getCode() == Status.Code.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public void setBranchHead(String repoKey, String branch, String commitHash) {
        SetBranchHeadResult result = repoStub(repoKey).setBranchHead(SetBranchHeadRequest.newBuilder()
                .setBranch(branch)
                .setDesired(commitId(commitHash))
                .build());
        if (result.getStaleHead()) {
            // no CAS intent on this interface: force the pointer with the observed head as guard
            repoStub(repoKey).setBranchHead(SetBranchHeadRequest.newBuilder()
                    .setBranch(branch)
                    .setExpectedHead(result.getCurrentHead())
                    .setDesired(commitId(commitHash))
                    .build());
        }
    }

    // ── Leases ───────────────────────────────────────────────────────────────

    @Override
    public String acquireSnapshotLease(String repoKey, String commitHash) {
        return repoStub(repoKey).acquireSnapshotLease(LeaseRequest.newBuilder()
                .setCommit(commitId(commitHash))
                .setTtlSeconds(LEASE_TTL_SECONDS)
                .build()).getLeaseId();
    }

    @Override
    public void renewSnapshotLease(String repoKey, String leaseId) {
        repoStub(repoKey).renewSnapshotLease(LeaseId.newBuilder().setLeaseId(leaseId).build());
    }

    @Override
    public void releaseSnapshotLease(String repoKey, String leaseId) {
        repoStub(repoKey).releaseSnapshotLease(LeaseId.newBuilder().setLeaseId(leaseId).build());
    }

    // ── Stubs & codecs ───────────────────────────────────────────────────────

    private ProllyVersioningGrpc.ProllyVersioningBlockingStub lifecycleStub() {
        Metadata headers = new Metadata();
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(API_KEY_HEADER, apiKey);
        }
        return ProllyVersioningGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private ProllyVersioningGrpc.ProllyVersioningBlockingStub repoStub(String repoKey) {
        Metadata headers = new Metadata();
        headers.put(REPO_HEADER, repoKey);
        if (apiKey != null && !apiKey.isBlank()) {
            headers.put(API_KEY_HEADER, apiKey);
        }
        return ProllyVersioningGrpc.newBlockingStub(channel)
                .withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    private static CommitId commitId(String hex) {
        return CommitId.newBuilder().setHash(ByteString.copyFrom(HexFormat.of().parseHex(hex))).build();
    }

    private static String hex(CommitId id) {
        return HexFormat.of().formatHex(id.getHash().toByteArray());
    }

    /** Decodes a self-contained batch, feeding INSERTs and DELETEs to their consumers. */
    private void decodeBatch(QuadChangeBatch batch, java.util.function.Consumer<Statement> inserts,
                             java.util.function.Consumer<Statement> deletes) {
        List<String> terms = batch.getDict().getTermsList();
        for (QuadChange change : batch.getChangesList()) {
            Quad quad = change.getQuad();
            Resource subject = NTriplesUtil.parseResource(terms.get(quad.getS() - 1), vf);
            IRI predicate = NTriplesUtil.parseURI(terms.get(quad.getP() - 1), vf);
            Value object = NTriplesUtil.parseValue(terms.get(quad.getO() - 1), vf);
            Statement st = vf.createStatement(subject, predicate, object);
            if (change.getKind() == ChangeKind.DELETE) {
                deletes.accept(st);
            } else {
                inserts.accept(st);
            }
        }
    }

    /** Encodes additions (INSERT) + deletions (DELETE) into one interned, self-contained batch. */
    private QuadChangeBatch encodeBatch(Model additions, Model deletions) {
        Map<String, Integer> interned = new HashMap<>();
        List<String> terms = new ArrayList<>();
        QuadChangeBatch.Builder batch = QuadChangeBatch.newBuilder();
        encodeInto(batch, additions, ChangeKind.INSERT, interned, terms);
        encodeInto(batch, deletions, ChangeKind.DELETE, interned, terms);
        batch.setDict(TermDictionary.newBuilder().addAllTerms(terms));
        return batch.build();
    }

    private void encodeInto(QuadChangeBatch.Builder batch, Model model, ChangeKind kind,
                            Map<String, Integer> interned, List<String> terms) {
        for (Statement st : model) {
            batch.addChanges(QuadChange.newBuilder()
                    .setKind(kind)
                    .setQuad(Quad.newBuilder()
                            .setS(intern(NTriplesUtil.toNTriplesString(st.getSubject()), interned, terms))
                            .setP(intern(NTriplesUtil.toNTriplesString(st.getPredicate()), interned, terms))
                            .setO(intern(NTriplesUtil.toNTriplesString(st.getObject()), interned, terms))
                            .setG(0)));
        }
    }

    private static int intern(String term, Map<String, Integer> interned, List<String> terms) {
        return interned.computeIfAbsent(term, t -> {
            terms.add(t);
            return terms.size();
        });
    }
}
