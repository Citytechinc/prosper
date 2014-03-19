package com.citytechinc.aem.prosper.specs

import com.citytechinc.aem.groovy.extension.builders.NodeBuilder
import com.citytechinc.aem.groovy.extension.builders.PageBuilder
import com.citytechinc.aem.groovy.extension.metaclass.GroovyExtensionMetaClassRegistry
import com.citytechinc.aem.prosper.builders.RequestBuilder
import com.citytechinc.aem.prosper.builders.ResponseBuilder
import com.citytechinc.aem.prosper.mocks.adapter.TestAdaptable
import com.citytechinc.aem.prosper.mocks.resource.MockResourceResolver
import com.citytechinc.aem.prosper.mocks.resource.TestResourceResolver
import com.day.cq.commons.jcr.JcrConstants
import com.day.cq.tagging.TagManager
import com.day.cq.tagging.impl.JcrTagManagerImpl
import com.day.cq.wcm.api.NameConstants
import com.day.cq.wcm.api.Page
import com.day.cq.wcm.api.PageManager
import com.day.cq.wcm.core.impl.PageImpl
import com.day.cq.wcm.core.impl.PageManagerFactoryImpl
import groovy.transform.Synchronized
import org.apache.sling.api.adapter.AdapterFactory
import org.apache.sling.api.resource.Resource
import org.apache.sling.api.resource.ResourceResolver
import org.apache.sling.api.resource.ValueMap
import org.apache.sling.commons.testing.jcr.RepositoryUtil
import org.apache.sling.jcr.api.SlingRepository
import org.apache.sling.jcr.resource.JcrPropertyMap
import spock.lang.Shared
import spock.lang.Specification

import javax.jcr.Node
import javax.jcr.Session

/**
 * Spock specification for AEM testing that includes a Sling <code>ResourceResolver</code> and content builders.
 */
@SuppressWarnings("deprecation")
abstract class ProsperSpec extends Specification implements TestAdaptable {

    private static final def SYSTEM_NODE_NAMES = ["jcr:system", "rep:policy"]

    private static final def NODE_TYPES = ["sling", "replication", "tagging", "core", "dam", "vlt"]

    private static SlingRepository repository

    @Shared sessionInternal

    @Shared resourceResolverInternal

    @Shared pageManagerInternal

    @Shared nodeBuilderInternal

    @Shared pageBuilderInternal

    @Shared adapterFactories = []

    @Shared resourceResolverAdapters = [:]

    @Shared resourceAdapters = [:]

    // global fixtures

    /**
     * Create an administrative JCR session with content builders, register Sling adapters,
     * and instantiate a mock resource resolver.
     */
    def setupSpec() {
        GroovyExtensionMetaClassRegistry.registerMetaClasses()

        sessionInternal = getRepository().loginAdministrative(null)
        nodeBuilderInternal = new NodeBuilder(sessionInternal)
        pageBuilderInternal = new PageBuilder(sessionInternal)

        addAdapters()

        resourceResolverInternal = new MockResourceResolver(sessionInternal, resourceResolverAdapters, resourceAdapters,
            adapterFactories)
        pageManagerInternal = resourceResolver.adaptTo(PageManager)
    }

    /**
     * Remove all non-system nodes to cleanup any test data and logout of the JCR session.
     */
    def cleanupSpec() {
        GroovyExtensionMetaClassRegistry.removeMetaClasses()

        removeAllNodes()

        sessionInternal.logout()
    }

    /**
     * Get a new page manager to ensure all caches are cleared.
     */
    def setup() {
        pageManagerInternal = resourceResolver.adaptTo(PageManager)
    }

    // default adapter methods return empty collections

    /**
     * Add <code>AdapterFactory</code> instances for adapting <code>Resource</code> or <code>ResourceResolver</code>
     * instances to different types at test runtime.  Specs should override this method to add testing adapter
     * factories at runtime.
     *
     * @return collection of Sling adapter factories
     */
    Collection<AdapterFactory> addAdapterFactories() {
        Collections.emptyList()
    }

    /**
     * Add <code>Resource</code> adapters and their associated adapter functions.  The mapped closure will be called
     * with a single <code>Resource</code> argument.  Specs should override this method to add resource adapters at
     * runtime.
     *
     * @return map of adapter types to adapter functions
     */
    Map<Class, Closure> addResourceAdapters() {
        Collections.emptyMap()
    }

    /**
     * Add <code>ResourceResolver</code> adapters and their associated adapter functions. The mapped closure will be
     * called with a single <code>ResourceResolver</code> argument.  Specs should override this method to add
     * resource resolver adapters at runtime.
     *
     * @return map of adapter types to adapter functions
     */
    Map<Class, Closure> addResourceResolverAdapters() {
        Collections.emptyMap()
    }

    /**
     * Add a <code>Resource</code> adapter for the current specification.  This method can be called as many times as
     * necessary in a feature method to add adapters for the current test.
     *
     * @param adapterType adapter class
     * @param closure closure with a single <code>Resource</code> that returns an instance of the adapter class
     */
    @Override
    void addResourceAdapter(Class adapterType, Closure closure) {
        resourceResolverInternal.addResourceAdapter(adapterType, closure)
    }

    /**
     * Add a <code>ResourceResolver</code> adapter for the current specification.  This method can be called as many
     * times as necessary in a feature method to add adapters for the current test.
     *
     * @param adapterType adapter class
     * @param closure closure with a single <code>ResourceResolver</code> that returns an instance of the adapter class
     */
    @Override
    void addResourceResolverAdapter(Class adapterType, Closure closure) {
        resourceResolverInternal.addResourceResolverAdapter(adapterType, closure)
    }

    // accessors for shared instances

    /**
     * @return JCR node builder
     */
    NodeBuilder getNodeBuilder() {
        nodeBuilderInternal
    }

    /**
     * @return CQ page builder
     */
    PageBuilder getPageBuilder() {
        pageBuilderInternal
    }

    /**
     * @return admin resource resolver
     */
    TestResourceResolver getResourceResolver() {
        resourceResolverInternal
    }

    /**
     * @return admin session
     */
    Session getSession() {
        sessionInternal
    }

    /**
     * @return CQ page manager
     */
    PageManager getPageManager() {
        pageManagerInternal
    }

    /**
     * Get the Node for a path.
     *
     * @param path valid JCR Node path
     * @return node for given path
     */
    Node getNode(String path) {
        sessionInternal.getNode(path)
    }

    /**
     * Get the Resource for a path.
     *
     * @param path valid Resource path
     * @return resource for given path or null
     */
    Resource getResource(String path) {
        resourceResolverInternal.getResource(path)
    }

    /**
     * Get the Page for a path.
     *
     * @param path valid Page path
     * @return Page for given path or null
     */
    Page getPage(String path) {
        pageManagerInternal.getPage(path)
    }

    // builders

    /**
     * Get a request builder.  If the path is not specified as an argument to the <code>build()
     * </code> closure, the root resource will be bound to the request.
     *
     * @return request builder instance for this resource resolver
     */
    RequestBuilder getRequestBuilder() {
        new RequestBuilder(resourceResolverInternal)
    }

    /**
     * Get a response builder.
     *
     * @return builder
     */
    ResponseBuilder getResponseBuilder() {
        new ResponseBuilder()
    }

    /**
     * Remove all non-system nodes to cleanup any test data.  This method would typically be called from a test fixture
     * method to cleanup content before the entire specification has been executed.
     */
    void removeAllNodes() {
        sessionInternal.rootNode.nodes.findAll { !SYSTEM_NODE_NAMES.contains(it.name) }*.remove()
        sessionInternal.save()
    }

    // assertion methods for use in Spock specification 'expect' blocks

    /**
     * Assert that a node exists for the given path.
     *
     * @param path node path
     */
    void assertNodeExists(String path) {
        assert sessionInternal.nodeExists(path)
    }

    /**
     * Assert that a node exists for the given path and node type.
     *
     * @param path node path
     * @param primaryNodeTypeName primary node type name
     */
    void assertNodeExists(String path, String primaryNodeTypeName) {
        assert sessionInternal.nodeExists(path)

        def node = sessionInternal.getNode(path)

        assert node.primaryNodeType.name == primaryNodeTypeName
    }

    /**
     * Assert that a node exists for the given path and property map.
     *
     * @param path node path
     * @param properties map of property names and values to verify for the node
     */
    void assertNodeExists(String path, Map<String, Object> properties) {
        assert sessionInternal.nodeExists(path)

        def node = sessionInternal.getNode(path)

        properties.each { name, value ->
            assert node.get(name) == value
        }
    }

    /**
     * Assert that a node exists for the given path, node type, and property map.
     *
     * @param path node path
     * @param primaryNodeTypeName primary node type name
     * @param properties map of property names and values to verify for the node
     */
    void assertNodeExists(String path, String primaryNodeTypeName, Map<String, Object> properties) {
        assert sessionInternal.nodeExists(path)

        def node = sessionInternal.getNode(path)

        assert node.primaryNodeType.name == primaryNodeTypeName

        properties.each { name, value ->
            assert node.get(name) == value
        }
    }

    /**
     * Assert that a page exists for the given path.
     *
     * @param path page path
     */
    void assertPageExists(String path) {
        assert session.nodeExists(path)

        def pageNode = session.getNode(path)

        assert pageNode.primaryNodeType.name == NameConstants.NT_PAGE
        assert pageNode.hasNode(JcrConstants.JCR_CONTENT)
    }

    /**
     * Assert that a page exists for the given path and contains the given properties.
     *
     * @param path page path
     * @param properties map of property names and values to verify for the page
     */
    void assertPageExists(String path, Map<String, Object> properties) {
        assertPageExists(path)

        def contentNode = session.getNode(path).getNode(JcrConstants.JCR_CONTENT)

        properties.each { name, value ->
            assert contentNode.get(name) == value
        }
    }

    // internals

    @Synchronized
    private def getRepository() {
        if (!repository) {
            RepositoryUtil.startRepository()

            repository = RepositoryUtil.getRepository()

            registerNodeTypes()

            addShutdownHook {
                RepositoryUtil.stopRepository()
            }
        }

        repository
    }

    private def registerNodeTypes() {
        sessionInternal = getRepository().loginAdministrative(null)

        NODE_TYPES.each { type ->
            this.class.getResourceAsStream("/SLING-INF/nodetypes/${type}.cnd").withStream { InputStream stream ->
                RepositoryUtil.registerNodeType(sessionInternal, stream)
            }
        }

        sessionInternal.logout()
    }

    private void addAdapters() {
        adapterFactories.addAll(addAdapterFactories())

        addDefaultResourceAdapters()
        addDefaultResourceResolverAdapters()

        resourceAdapters.putAll(addResourceAdapters())
        resourceResolverAdapters.putAll(addResourceResolverAdapters())
    }

    private void addDefaultResourceAdapters() {
        resourceAdapters[Page.class] = { Resource resource ->
            NameConstants.NT_PAGE == resource.resourceType ? new PageImpl(resource) : null
        }

        resourceAdapters[ValueMap.class] = { Resource resource ->
            def node = sessionInternal.getNode(resource.path)

            new JcrPropertyMap(node)
        }

        resourceAdapters[Node.class] = { Resource resource ->
            sessionInternal.getNode(resource.path)
        }
    }

    private void addDefaultResourceResolverAdapters() {
        resourceResolverAdapters[PageManager.class] = { ResourceResolver resourceResolver ->
            def factory = new PageManagerFactoryImpl()

            factory.getPageManager(resourceResolver)
        }

        resourceResolverAdapters[TagManager.class] = { ResourceResolver resourceResolver ->
            new JcrTagManagerImpl(resourceResolver, null, null, "/etc/tags")
        }

        resourceResolverAdapters[Session.class] = { sessionInternal }
    }
}