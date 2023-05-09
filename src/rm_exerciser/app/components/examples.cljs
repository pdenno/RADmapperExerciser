(ns rm-exerciser.app.components.examples)

(declare rm-examples elena-schemas)

(defn get-example [name]
  (some #(when (= name (:name %)) %) rm-examples))

;;; ($get [["schema/name" "urn:oagis-10.8.4:Nouns:Invoice"],  ["schema-object"]])
(def rm-examples
  [{:name "2023-04-19, (0) : Simple get"
    :code "( $schema := $get([['schema/name', 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_1'], ['schema/content']]);
           $schema )"}

   {:name "2023-04-19, (1) : JSONata-like"
    :code
    "// Ordinary JSONata-like expressions: get the names of the two schema in the LHS pane:

[$s1, $s2].`schema/name`"
    :data elena-schemas}

   {:name "2023-04-19, (2) : Simplest query"
    :code
"(
  $x := {'element/name' : 'foo'};
  $qf := query{[?x :element/name ?name]};
  $qf($x)
)"
    :data elena-schemas}

   {:name "2023-04-19, (3): Simple query, complicated schema"
    :code
    "(
  // Small bug in the exerciser (because it combines data from the LHS pane):
  // currently comments have to be inside the open paren.
  // Here we put $s1 and $s2 into a vector, $db, so we can work on them together.
  // We could also just call the query on either $s1 or $s2, of course,
  // or don't create the $db and just call $qf with [$s1, $s2].

 $db := [$s1, $s2];
 $qf := query{[?x :schema/name ?name]};
 $qf($db)
)"
    :data elena-schemas}

   {:name "2023-04-19, (*): (aside) Query defines a function."
    :code
    "(
  // Remember: query and express are function defining.
  // If you run the following you just get <<function>>

  query{[?x :model/elementDef ?ed]}
)"}

   {:name "2023-04-19, (4):  query :model/elementDef"
    :code
    "(
  // This example queries for all the element definitions.
  // The :model/elementDefs are objects (things inside curly brackets)
  // so the are represented by a unique integer (entity IDs).
  // They aren't too interesting; just used to navigate through the nested structure.
  // Note that the entity ID are small numbers because we aren't running RM in the server.
  // The only entities we know about are the ones in the LHS pane.

 $qf := query{[?x :model/elementDef ?ed]};
 $qf($s1)
)"
    :data elena-schemas}

   {:name "2023-04-19, (*): $get (next time!)"
    :code
    "(
  // I really didn't want to have all that data in the LHS pane today.
  // I wanted to call $get with a graph query, like this.
  // The following DOES NOT WORK (today).

 $schemaNames := $get([['list/id', 'ccts/message-schema'], ['schema/name']]); // How I might get the strings below.

 $db := [$get([['schema/name', 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_1'],  ['schema/content']]),
         $get([['schema/name', 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_2'],  ['schema/content']])];

 $qf := query{[?x :schema/name ?name]};
 $qf($db)
)"}

   {:name "2023-04-19, (5): Towards goal: query :element/name"
    :code
    "(
  // We'll start working towards something useful with the two schema.
  // In the next few examples, we'll discover how they differ.
  // Let's start by listing  all the element names in each schema.

  $qf := query{[?x :element/name ?name]};

  {'schema 1': $qf($s1),
   'schema 2': $qf($s2)}
)"
    :data elena-schemas}

   {:name "2023-04-19, (6): Child elements"
    :code
    "(
  // Let's find the children of an element.
  // In the schema design, I tried to give things meaningful names
  // (of course CCT names didn't change).
  // 'model/sequence' is supposed to be the general notion of a sequence of things.
  // There are probably a few patterns in the schema data for getting parent/child relationships.
  // For this data, however, there is only one pattern;
  // it starts with :model/sequence and ends with :element/name.
  // We could use datalog rules to catch pattern... it is on my ToDo list.
  // With this pattern with might just do $query{(parentChild ?parent ?child)}.

  $qf := query{[?x :element/name ?parent]
               [?x :element/complexType ?cplx1]
               [?cplx1 :model/sequence ?def]      // The pattern 'starts over again'.
               [?def   :model/elementDef ?cplx2]
               [?cplx2 :element/name ?child]};

  {'schema 1': $qf($s1),
   'schema 2': $qf($s2)}
)"
    :data elena-schemas}

   {:name "2023-04-19, (7): Roots"
    :code
    "(
  // The two lists we generated in (6) each have one less element than the lists
  // we generated in (5), where we were just pulling out :element/name, wherever it occurs.
  // Of course this is because root elements don't have parents.
  // I suppose there are two patterns in the schema for picking off roots:
  // (1) :schema/content ->                    :model/elementDef -> :element/name.
  // (2) :schema/content -> :model/sequence -> :model/elementDef -> :element/name.

  $qf1 := query{[?c :schema/content ?e]      // pattern 1
                [?e :model/elementDef ?d]
                [?d :element/name ?name]};

  $qf2 := query{[?c :schema/content ?e]      // pattern 2
                [?e :model/sequence ?s]
                [?s :model/elementDef ?d]
                [?d :element/name ?name]};


  {'pattern 1': {'schema 1': {'roots': $qf1($s1)},
                 'schema 2': {'roots': $qf1($s2)}},
   'pattern 2': {'schema 1': {'roots': $qf2($s1)},
                 'schema 2': {'roots': $qf2($s2)}}};
)"
    :data elena-schemas}

   {:name "2023-04-19, (8): Nested structures"
    :code
    "(
  // The two lists we generated in (6) each have one less element than the lists
  // we generated in (5), where we were just pulling out :element/name, wherever it occurs.
  // Of course this is because root elements don't have parents.
  // I suppose there are two patterns in the schema for picking off roots:
  // (1) :schema/content ->                    :model/elementDef -> :element/name.
  // (2) :schema/content -> :model/sequence -> :model/elementDef -> :element/name.

  $qfRoots := query{[?c :schema/content ?e]      // pattern 1
                    [?e :model/elementDef ?d]
                    [?d :element/name ?name]};

    //ToDo: $roots1 := $qfRoots($s1).`?name`;
    //ToDo: $roots2 := $qfRoots($s2).`?name`;
    //ToDo: $roots := [$roots1, roots2];
    $roots := [['ProcessInvoice'], ['ProcessInvoice']];


)"
    :data elena-schemas}

   {:name "2023-04-19, (9): Shape and $semMatch"
    :code
   "(
  $schema1 := $get([['schema/name', 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_1'], ['schema/content']]);
  $schema2 := $get([['schema/name', 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_2'], ['schema/content']]);

  $pcQuery := query{[?x     :element/name        ?parent] // pc = 'parent/child'
                    [?x     :element/complexType ?cplx1]
                    [?cplx1 :model/sequence      ?def]
                    [?def   :model/elementDef    ?cplx2]
                    [?cplx2 :element/name        ?child]};

  $rootQuery := query{[?c :schema/content   ?e]
                      [?e :model/elementDef ?d]
                      [?d :element/name     ?name]};

  // This function just gets the children for a parent.
  $children := function($spc, $p) { $spc[?parent = $p].?child };

  // This function calls itself recursively to build the schema shape, starting from the root.
  $shape := function($p, $spc) { $reduce($children($spc, $p),
                                         function($tree, $c) // Update the tree.
                                             { $update($tree,
                                                       $p,
                                                       function($x) { $assoc($x, $c, $lookup($shape($c, $spc), $c) or '<data>')}) },
                                         {})};

  $schema1PC    := $pcQuery($schema1);     // Call the two queries with the two schema.
  $schema2PC    := $pcQuery($schema2);     // The first two return binding sets for {?parent x ?child y}
  $schema1Roots := $rootQuery($schema1);   // The last two return binding sets for {?name} (of a root).
  $schema2Roots := $rootQuery($schema2);

  {'shape1' : $shape($schema1Roots.?name[0], $schema1PC),
   'shape2' : $shape($schema2Roots.?name[0], $schema2PC)}

  // $semMatch($shape($schema1Roots.?name[0], $schema1PC), // [0] here is cheating a bit; there could be multiple roots.
  //           $shape($schema2Roots.?name[0], $schema2PC))

)"}


   {:name "Schema from server"
    :code "$get([['schema/name', 'urn:oagis-10.8.4:Nouns:Invoice'], ['schema-object']])"}

   {:name "2 Databases"
    :code
    "( $qFn :=  query(){[$DBa ?e1 :email ?id]
                   [$DBb ?e2 :id    ?id]
                   [$DBa ?e1 :name  ?name]
                   [$DBa ?e1 :aAttr ?aData]
                   [$DBb ?e2 :bAttr ?bData]};

  $bSet := $qFn($DBa, $DBb);

  $eFn := express(){{?id : {'name'  : ?name,
                            'aData' : ?aData,
                            'bData' : ?bData}}};

  $reduce($bSet, $eFn)
)"
    :data
    "  $DBa := [{'email' : 'bob@example.com', 'aAttr' : 'Bob-A-data',   'name' : 'Bob'},
           {'email' : 'alice@alice.org', 'aAttr' : 'Alice-A-data', 'name' : 'Alice'}];

  $DBb := [{'id' : 'bob@example.com', 'bAttr' : 'Bob-B-data'},
           {'id' : 'alice@alice.org', 'bAttr' : 'Alice-B-data'}];"}


;;;======================= Simple queries
   {:name "Simple queries"
    :code
    "( $DBa := [{'id' : 123, 'aAttr' : 'A-value'}];
  $DBb := [{'id' : 123, 'bAttr' : 'B-value'}];

  // query returns a function.
  $aFn := query{[?e :aAttr ?aData]};
  $bFn := query{[?e :bAttr ?bData]};

  // Call the function on data.
  $aRes := $aFn($DBa);
  $bRes := $bFn($DBb);

  // Get back binding sets.
  [$aRes, $bRes]
)"
    :data ""}])

(def elena-schemas
"
$s1 :=
{'schema/name'       : 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_1',
  'schema/type'      : 'cct/bie',
  'schema/sdo'       : 'oagi',
  'schema/spec'      : 'oagis',
  'schema/version'   : '10',
  'schema/subversion': 'unknown',
  'schema/topic'     : '',
  'schema/pathname'  : '/opt/messaging/sources/misc/elena/2023-02-09/ProcessInvoice-BC_1.xsd',
  'schema/content'   :
  [{'model/elementDef':
    [{'element/name': 'ProcessInvoice',
      'element/complexType':
      [{'model/sequence':
        [{'model/elementDef':
          [{'element/name': 'ApplicationArea',
            'element/complexType':
            [{'model/sequence':
              [{'model/elementDef':
                [{'element/name': 'CreationDateTime',
                  'has/documentation':
                  [{'cct/BCC_GUID': 'oagis-id-44a813124c994a5182bdaf1c8bc617d1'},
                   {'cct/BCCRevisionNumber': 1},
                   {'cct/BCCDefinition': 'is the date time stamp that the given instance of the Business Object Document was created.  This date must not be modified during the life of the Business Object Document.',
                   'has/source'       : 'http://www.openapplications.org/oagis/10/platform/2'},
                   {'cct/BCCP_GUID': 'oagis-id-4ba8e6b8c9fb46cda2724a1770fa9baf'},
                   {'cct/BCCPRevisionNumber': 1},
                   {'cct/BCCPDefinition': 'is the date time stamp that the given instance of the Business Object Document was created.  This date must not be modified during the life of the Business Object Document.',
                   'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'}],
                  'xsd/maxOccurs'    :
                  1,
                  'xsd/minOccurs'    :
                  1,
                  'xsd/type'         :
                  'xsd:dateTime'}]}],
              'has/documentation':
              [{'cct/ACC_GUID': 'oagis-id-cc4d9ae9cbde4abe95a5a647caae9892'}, {'cct/ACCRevisionNumber': 1}]}],
            'has/documentation'  :
            [{'cct/ASCC_GUID': 'oagis-id-c2cb6823837d4149b32aefb8fd4120cd'},
             {'cct/ASCCRevisionNumber': 1},
             {'cct/ASCCDefinition':
              'Provides the information that an application may need to know in order to communicate in an integration of two or more business applications. The ApplicationArea is used at the applications layer of communication. While the integration frameworks web services and middleware provide the communication layer that OAGIS operates on top of.',
              'has/source'        :
              'http://www.openapplications.org/oagis/10/platform/2'},
             {'cct/ASCCP_GUID': 'oagis-id-e8f1f16759e440c2911522aaee3ae97c'},
             {'cct/ASCCPRevisionNumber': 1},
             {'cct/ASCCPDefinition':
              'Provides the information that an application may need to know in order to communicate in an integration of two or more business applications. The ApplicationArea is used at the applications layer of communication. While the integration frameworks web services and middleware provide the communication layer that OAGIS operates on top of.',
              'has/source'         :
              'http://www.openapplications.org/oagis/10/platform/2'}],
            'xsd/maxOccurs'      :
            1,
            'xsd/minOccurs'      :
            1}]},
         {'model/elementDef':
          [{'element/name': 'DataArea',
            'element/complexType':
            [{'model/sequence':
              [{'model/elementDef':
                [{'element/name': 'Process',
                  'element/complexType':
                  [{'has/documentation': [{'cct/ACC_GUID': 'oagis-id-35103021a6664dceb8e44102edde7c48'}, {'cct/ACCRevisionNumber': 1}]}],
                  'has/documentation'  :
                  [{'cct/ASCC_GUID': 'oagis-id-683805bd4049421e9ce3d3da8efaf630'},
                   {'cct/ASCCRevisionNumber': 1},
                   {'cct/ASCCP_GUID': 'oagis-id-325803d9f1d8493bb87e5b85735680a8'},
                   {'cct/ASCCPRevisionNumber': 1},
                   {'cct/ASCCPDefinition':
                    'The Process verb is used to request processing of the associated noun by the receiving application or business to party. In a typical external exchange scenario a Process BOD is considered to be a legally binding message. For example, if a customer sends a ProcessPurchaseOrder BOD to a supplier and the supplier acknowlegdes with a positive AcknowledgePurchaseOrder, then the customer is obligated to fullfil the agreement, unless of course other BODs are allowed to cancel or change the original order.',
                    'has/source'         :
                    'http://www.openapplications.org/oagis/10/platform/2'}],
                  'xsd/maxOccurs'      :
                  1,
                  'xsd/minOccurs'      :
                  1}]},
               {'model/elementDef':
                [{'element/name': 'Invoice',
                  'element/complexType':
                  [{'model/sequence':
                    [{'model/elementDef':
                      [{'element/name': 'InvoiceLine',
                        'element/complexType':
                        [{'model/sequence':
                          [{'model/elementDef':
                            [{'element/name': 'Item',
                              'element/complexType':
                              [{'model/sequence': [{'model/elementDef': [{'element/name': 'ManufacturingParty',
                                 'element/complexType': [{'model/sequence': [{'model/elementDef': [{'element/name': 'Name',
                                     'has/documentation': [{'cct/BCC_GUID': 'oagis-id-bfbc289c2f30409ebbb4ce6b3fe7ce2b'},
                                       {'cct/BCCRevisionNumber': 3},
                                       {'cct/BCCDefinition': 'Identifies the Name of the object in which associated.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                       {'cct/BCCP_GUID': 'oagis-id-1c34a054a4f24f7096db3ae76df3a825'},
                                       {'cct/BCCPRevisionNumber': 1},
                                       {'cct/BCCPDefinition': 'Identifies the Name of the object in which associated.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                     'xsd/maxOccurs'    : -1,
                                     'xsd/minOccurs'    : 0,
                                     'xsd/nillable'     : true,
                                     'xsd/type'         : 'xsd:string'}]}],
                                   'has/documentation': [{'cct/ACC_GUID': 'oagis-id-82916fbf485b4586b85bed61a3ae0066'}, {'cct/ACCRevisionNumber': 1}]}],
                                 'has/documentation'  : [{'cct/ASCC_GUID': 'oagis-id-124d85e9013f446fb908a15462436273'},
                                   {'cct/ASCCRevisionNumber': 7},
                                   {'cct/ASCCP_GUID': 'oagis-id-48d24f6ecd404a2486f338bc4088d0e7'},
                                   {'cct/ASCCPRevisionNumber': 1},
                                   {'cct/ASCCPDefinition': 'Identifies the party that manufactured the associated Item.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                 'xsd/maxOccurs'      : -1,
                                 'xsd/minOccurs'      : 0}]}],
                               'has/documentation': [{'cct/ACC_GUID': 'oagis-id-f21885bb90fb482c848fe4e3817550ac'},
                                 {'cct/ACCRevisionNumber': 1},
                                 {'cct/ACCDefinition': 'The Type for  detailing an object that expands on the Base description', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                              'has/documentation'  :
                              [{'cct/ASCC_GUID': 'oagis-id-12e6e8f816a242ecb98ae2bcb549103e'},
                               {'cct/ASCCRevisionNumber': 1},
                               {'cct/ASCCDefinition': 'Identifies the Item associated with the Line or Detail of a transaction. The item provides the details of the generic Item component information.',
                               'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'},
                               {'cct/ASCCP_GUID': 'oagis-id-2388e643990d44828b0b2c11a2ca4d4a'},
                               {'cct/ASCCPRevisionNumber': 1},
                               {'cct/ASCCPDefinition': 'Specifies details concerning a thing which could be a product, service or virtual things. Item typically provides the details of the generic Item information.',
                               'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                              'xsd/maxOccurs'      :
                              1,
                              'xsd/minOccurs'      :
                              0}]},
                           {'model/elementDef':
                            [{'element/name': 'BuyerParty',
                              'element/complexType':
                              [{'model/sequence':
                                [{'model/elementDef':
                                  [{'element/name': 'TaxIDSet',
                                    'element/complexType':
                                    [{'model/sequence':
                                      [{'model/elementDef':
                                        [{'element/name': 'ID',
                                          'has/documentation':
                                          [{'cct/BCC_GUID': 'oagis-id-59fccca73c224f96a9ae7ea4138c28cf'},
                                           {'cct/BCCRevisionNumber': 2},
                                           {'cct/BCCDefinition': 'a part of the composition identifier, itself an identifier that is only unique in a specific context.'},
                                           {'cct/BCCP_GUID': 'oagis-id-57f1dbe323d344caa1394f49f923bb89'},
                                           {'cct/BCCPRevisionNumber': 1},
                                           {'cct/BCCPDefinition':
                                            'Is the Identifiers of the given instance of an entity within the scope of the integration. The schemeAgencyID attribute identifies the party that provided or knows this party by the given identifier.',
                                            'has/source'        :
                                            'http://www.openapplications.org/oagis/10/platform/2'}],
                                          'xsd/maxOccurs'    :
                                          -1,
                                          'xsd/minOccurs'    :
                                          1,
                                          'xsd/nillable'     :
                                          true,
                                          'xsd/type'         :
                                          'cl_1_EU_Tax_IDContentType_da388e04bc7a4108ac8f890fc652d5c1'}]}],
                                      'has/documentation':
                                      [{'cct/ACC_GUID': 'oagis-id-8537e396814a488087b71496eaec6563'},
                                       {'cct/ACCRevisionNumber': 2},
                                       {'cct/ACCDefinition': 'A set of IDs to build a composite identifier of an object.', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                                    'has/documentation'  :
                                    [{'cct/ASCC_GUID': 'oagis-id-c1bea7eb473a481bb300a1ae5dd2f134'}, {'cct/ASCCRevisionNumber': 2}, {'cct/ASCCP_GUID': 'oagis-id-9ea03290b7c7478bb7409699f73e2c41'}, {'cct/ASCCPRevisionNumber': 1}],
                                    'xsd/maxOccurs'      :
                                    -1,
                                    'xsd/minOccurs'      :
                                    0}]},
                                 {'model/elementDef':
                                  [{'element/name': 'Location',
                                    'element/complexType':
                                    [{'model/sequence':
                                      [{'model/elementDef':
                                        [{'element/name': 'Address',
                                          'element/complexType':
                                          [{'model/sequence': [{'model/elementDef': [{'element/name': 'AddressLine',
                                             'has/documentation': [{'cct/BCC_GUID': 'oagis-id-e79f9148e276487ba5cc43f0c53e0c46'},
                                               {'cct/BCCRevisionNumber': 1},
                                               {'cct/BCCDefinition': 'An address line that contains a line of an address.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                               {'cct/BCCP_GUID': 'oagis-id-a74f6ce1584e4878ae64e86c88561333'},
                                               {'cct/BCCPRevisionNumber': 1}],
                                             'xsd/maxOccurs'    : -1,
                                             'xsd/minOccurs'    : 0,
                                             'xsd/type'         : 'xsd:string'}]}],
                                           'has/documentation': [{'cct/ACC_GUID': 'oagis-id-d3b32b38193c455b984bf9632c8dd78c'}, {'cct/ACCRevisionNumber': 1}]}],
                                          'has/documentation'  :
                                          [{'cct/ASCC_GUID': 'oagis-id-c4cf7383aec7416293a54161d8c61746'}, {'cct/ASCCRevisionNumber': 1}, {'cct/ASCCP_GUID': 'oagis-id-18764ba0412248ae8d2a8e56619fd970'}, {'cct/ASCCPRevisionNumber': 1}],
                                          'xsd/maxOccurs'      :
                                          -1,
                                          'xsd/minOccurs'      :
                                          0}]}],
                                      'has/documentation':
                                      [{'cct/ACC_GUID': 'oagis-id-ac71693f265d4554a6b7e663e4b6ec83'}, {'cct/ACCRevisionNumber': 1}]}],
                                    'has/documentation'  :
                                    [{'cct/ASCC_GUID': 'oagis-id-64a635149fa94f589ea87b8ead8c740f'},
                                     {'cct/ASCCRevisionNumber': 3},
                                     {'cct/ASCCDefinition': 'The location of a thing, as captured by an address (or addresses), GPS Coordinates, and/or in relation to other locations.',
                                     'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'},
                                     {'cct/ASCCP_GUID': 'oagis-id-5eecda64436b459e85510a774a6dd390'},
                                     {'cct/ASCCPRevisionNumber': 1},
                                     {'cct/ASCCPDefinition': 'The location of a thing, as captured by an address (or addresses), GPS Coordinates, and/or in relation to other locations.',
                                     'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                                    'xsd/maxOccurs'      :
                                    -1,
                                    'xsd/minOccurs'      :
                                    0}]}],
                                'has/documentation':
                                [{'cct/ACC_GUID': 'oagis-id-efa8bce0226244f5b2749b4b4667a19e'}, {'cct/ACCRevisionNumber': 1}]}],
                              'has/documentation'  :
                              [{'cct/ASCC_GUID': 'oagis-id-4e8e475d308048e2b006bd83d56a43d2'},
                               {'cct/ASCCRevisionNumber': 1},
                               {'cct/ASCCP_GUID': 'oagis-id-7da061d1ecd04284b46ad1bb4d81471c'},
                               {'cct/ASCCPRevisionNumber': 1},
                               {'cct/ASCCPDefinition': 'The entity that is responsible for Buying the document or element in which it is associated. Additionally, the contact  may be a person or a group or department.',
                               'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                              'xsd/maxOccurs'      :
                              1,
                              'xsd/minOccurs'      :
                              0}]}],
                          'has/documentation':
                          [{'cct/ACC_GUID': 'oagis-id-e8954e7c0d7f4180a00eab149e112c1e'},
                           {'cct/ACCRevisionNumber': 1},
                           {'cct/ACCDefinition': 'The InvoiceLine type supports order line invoicing.', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                        'has/documentation'  :
                        [{'cct/ASCC_GUID': 'oagis-id-d565da73a0dc4d93967df30bade554d9'},
                         {'cct/ASCCRevisionNumber': 1},
                         {'cct/ASCCDefinition':
                          'In general an Invoice Line can be aligned with Order Lines. The ItemQuantity and Price are used to compute the Total. The Total is a sum of the Total and any Charges, Taxes or Allowances.',
                          'has/source'        :
                          'http://www.openapplications.org/oagis/10'},
                         {'cct/ASCCP_GUID': 'oagis-id-57a00bde1ac9493d9e432ed9caf011e1'},
                         {'cct/ASCCPRevisionNumber': 1},
                         {'cct/ASCCPDefinition':
                          'In general an Invoice Line can be aligned with Order Lines. The ItemQuantity and Price are used to compute the Total. The Total is a sum of the Total and any Charges, Taxes or Allowances.',
                          'has/source'         :
                          'http://www.openapplications.org/oagis/10'}],
                        'xsd/maxOccurs'      :
                        -1,
                        'xsd/minOccurs'      :
                        0}]}],
                    'has/documentation':
                    [{'cct/ACC_GUID': 'oagis-id-9a47afef92474a61b6ab64247f41a97c'}, {'cct/ACCRevisionNumber': 1}]}],
                  'has/documentation'  :
                  [{'cct/ASCC_GUID': 'oagis-id-9f49996f77c24b2c9fb92d5770a7dc5d'},
                   {'cct/ASCCRevisionNumber': 1},
                   {'cct/ASCCP_GUID': 'oagis-id-eecc2a8ba86e4a1eac8bf19977a3d9d0'},
                   {'cct/ASCCPRevisionNumber': 1},
                   {'cct/ASCCPDefinition': 'The Invoice is used to invoice a customer for goods/services the have been provided.', 'has/source': 'http://www.openapplications.org/oagis/10'}],
                  'xsd/maxOccurs'      :
                  -1,
                  'xsd/minOccurs'      :
                  1}]}],
              'has/documentation':
              [{'cct/ACC_GUID': 'oagis-id-9f8525e4a22944c894bb7943c6de52bb'}, {'cct/ACCRevisionNumber': 1}]}],
            'has/documentation'  :
            [{'cct/ASCC_GUID': 'oagis-id-8859aa7fea054ed2981af5a9be991e6b'},
             {'cct/ASCCRevisionNumber': 1},
             {'cct/ASCCDefinition':
              'Is where the information that the BOD message carries is provided, in this case ProcessInvoice. The information consists of a Verb and one or more Nouns. The verb (Process) indicates the action to be performed on the Noun (Invoice).',
              'has/source'        :
              'http://www.openapplications.org/oagis/10'},
             {'cct/ASCCP_GUID': 'oagis-id-8859aa7fea054ed2981af5a9be991e6b'},
             {'cct/ASCCPRevisionNumber': 1},
             {'cct/ASCCPDefinition':
              'Is where the information that the BOD message carries is provided, in this case ProcessInvoice. The information consists of a Verb and one or more Nouns. The verb (Process) indicates the action to be performed on the Noun (Invoice).',
              'has/source'         :
              'http://www.openapplications.org/oagis/10'}],
            'xsd/maxOccurs'      :
            1,
            'xsd/minOccurs'      :
            1}]}],
        'has/documentation':
        [{'cct/ACC_GUID': 'oagis-id-b51a29bbe9c94ae986d44ddcd9d7f627'}, {'cct/ACCRevisionNumber': 1}]}],
      'has/documentation'  :
      [{'cct/BusinessContext': [{'cct/GUID': 'oagis-id-1d68712f1ff44355bb6b43e2d1862484', 'cct/Name': 'Bc_1'}]},
       {'cct/ASCCP_GUID': 'oagis-id-7814973e3d19488ba917cb61ac28b257'},
       {'cct/ASCCPRevisionNumber': 1},
       {'cct/ASCCPDefinition': 'The purpose of the ProcessInvoice is to transmit an invoice from a supplier to a customer. Indicating that the receiver of the Invoice is to Process the Invoice for payment.',
       'has/source'         : 'http://www.openapplications.org/oagis/10'}]}]},
   {'model/simpleType': [{'model/restriction': [{'restriction/attributes': [{'xsd/base': ['xsd:normalizedString']}]}]}]}],
  'schema/attributes':
  [{'xsd/attributeFormDefault': 'unqualified', 'xsd/elementFormDefault': 'qualified', 'xsd/targetNamespace': 'http://www.openapplications.org/oagis/10'}]};



  $s2 :=
{'schema/name'       : 'urn:oagi-10.unknown:elena.2023-02-09.ProcessInvoice-BC_2',
  'schema/type'      : 'cct/bie',
  'schema/sdo'       : 'oagi',
  'schema/spec'      : 'oagis',
  'schema/version'   : '10',
  'schema/subversion': 'unknown',
  'schema/topic'     : '',
  'schema/pathname'  :'/opt/messaging/sources/misc/elena/2023-02-09/ProcessInvoice-BC_2.xsd',
  'schema/content'   :
  [{'model/elementDef':
    [{'element/name': 'ProcessInvoice',
      'element/complexType':
      [{'model/sequence':
        [{'model/elementDef':
          [{'element/name': 'ApplicationArea',
            'element/complexType':
            [{'model/sequence':
              [{'model/elementDef':
                [{'element/name': 'CreationDateTime',
                  'has/documentation':
                  [{'cct/BCC_GUID': 'oagis-id-44a813124c994a5182bdaf1c8bc617d1'},
                   {'cct/BCCP_GUID': 'oagis-id-4ba8e6b8c9fb46cda2724a1770fa9baf'},
                   {'cct/BCCRevisionNumber': 1},
                   {'cct/BCCDefinition': 'is the date time stamp that the given instance of the Business Object Document was created.  This date must not be modified during the life of the Business Object Document.',
                   'has/source'       : 'http://www.openapplications.org/oagis/10/platform/2'},
                   {'cct/BCCPRevisionNumber': 1},
                   {'cct/BCCPDefinition': 'is the date time stamp that the given instance of the Business Object Document was created.  This date must not be modified during the life of the Business Object Document.',
                   'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'}],
                  'xsd/maxOccurs'    :
                  1,
                  'xsd/minOccurs'    :
                  1,
                  'xsd/type'         :
                  'xsd:dateTime'}]}],
              'has/documentation':
              [{'cct/ACC_GUID': 'oagis-id-cc4d9ae9cbde4abe95a5a647caae9892'}, {'cct/ACCRevisionNumber': 1}]}],
            'has/documentation'  :
            [{'cct/ASCC_GUID': 'oagis-id-c2cb6823837d4149b32aefb8fd4120cd'},
             {'cct/ASCCP_GUID': 'oagis-id-e8f1f16759e440c2911522aaee3ae97c'},
             {'cct/ASCCRevisionNumber': 1},
             {'cct/ASCCDefinition':
              'Provides the information that an application may need to know in order to communicate in an integration of two or more business applications. The ApplicationArea is used at the applications layer of communication. While the integration frameworks web services and middleware provide the communication layer that OAGIS operates on top of.',
              'has/source'        :
              'http://www.openapplications.org/oagis/10/platform/2'},
             {'cct/ASCCPRevisionNumber': 1},
             {'cct/ASCCPDefinition':
              'Provides the information that an application may need to know in order to communicate in an integration of two or more business applications. The ApplicationArea is used at the applications layer of communication. While the integration frameworks web services and middleware provide the communication layer that OAGIS operates on top of.',
              'has/source'         :
              'http://www.openapplications.org/oagis/10/platform/2'}],
            'xsd/maxOccurs'      :
            1,
            'xsd/minOccurs'      :
            1}]},
         {'model/elementDef':
          [{'element/name': 'DataArea',
            'element/complexType':
            [{'model/sequence':
              [{'model/elementDef':
                [{'element/name': 'Process',
                  'element/complexType':
                  [{'has/documentation': [{'cct/ACC_GUID': 'oagis-id-35103021a6664dceb8e44102edde7c48'}, {'cct/ACCRevisionNumber': 1}]}],
                  'has/documentation'  :
                  [{'cct/ASCC_GUID': 'oagis-id-683805bd4049421e9ce3d3da8efaf630'},
                   {'cct/ASCCP_GUID': 'oagis-id-325803d9f1d8493bb87e5b85735680a8'},
                   {'cct/ASCCRevisionNumber': 1},
                   {'cct/ASCCPRevisionNumber': 1},
                   {'cct/ASCCPDefinition':
                    'The Process verb is used to request processing of the associated noun by the receiving application or business to party. In a typical external exchange scenario a Process BOD is considered to be a legally binding message. For example, if a customer sends a ProcessPurchaseOrder BOD to a supplier and the supplier acknowlegdes with a positive AcknowledgePurchaseOrder, then the customer is obligated to fullfil the agreement, unless of course other BODs are allowed to cancel or change the original order.',
                    'has/source'         :
                    'http://www.openapplications.org/oagis/10/platform/2'}],
                  'xsd/maxOccurs'      :
                  1,
                  'xsd/minOccurs'      :
                  1}]},
               {'model/elementDef':
                [{'element/name': 'Invoice',
                  'element/complexType':
                  [{'model/sequence':
                    [{'model/elementDef':
                      [{'element/name': 'InvoiceLine',
                        'element/complexType':
                        [{'model/sequence':
                          [{'model/elementDef':
                            [{'element/name': 'Item',
                              'element/complexType':
                              [{'model/sequence': [{'model/elementDef': [{'element/name': 'ManufacturingParty',
                                 'element/complexType': [{'model/sequence': [{'model/elementDef': [{'element/name': 'Name',
                                     'has/documentation': [{'cct/BCC_GUID': 'oagis-id-bfbc289c2f30409ebbb4ce6b3fe7ce2b'},
                                       {'cct/BCCP_GUID': 'oagis-id-1c34a054a4f24f7096db3ae76df3a825'},
                                       {'cct/BCCRevisionNumber': 2},
                                       {'cct/BCCDefinition': 'Identifies the Name of the object in which associated.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                       {'cct/BCCPRevisionNumber': 1},
                                       {'cct/BCCPDefinition': 'Identifies the Name of the object in which associated.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                     'xsd/maxOccurs'    : -1,
                                     'xsd/minOccurs'    : 0,
                                     'xsd/type'         : 'xsd:string'}]}],
                                   'has/documentation': [{'cct/ACC_GUID': 'oagis-id-82916fbf485b4586b85bed61a3ae0066'}, {'cct/ACCRevisionNumber': 1}]}],
                                 'has/documentation'  : [{'cct/ASCC_GUID': 'oagis-id-124d85e9013f446fb908a15462436273'},
                                   {'cct/ASCCP_GUID': 'oagis-id-48d24f6ecd404a2486f338bc4088d0e7'},
                                   {'cct/ASCCRevisionNumber': 6},
                                   {'cct/ASCCPRevisionNumber': 1},
                                   {'cct/ASCCPDefinition': 'Identifies the party that manufactured the associated Item.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                 'xsd/maxOccurs'      : 1,
                                 'xsd/minOccurs'      : 0}]}],
                               'has/documentation': [{'cct/ACC_GUID': 'oagis-id-f21885bb90fb482c848fe4e3817550ac'},
                                 {'cct/ACCRevisionNumber': 1},
                                 {'cct/ACCDefinition': 'The Type for  detailing an object that expands on the Base description', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                              'has/documentation'  :
                              [{'cct/ASCC_GUID': 'oagis-id-12e6e8f816a242ecb98ae2bcb549103e'},
                               {'cct/ASCCP_GUID': 'oagis-id-2388e643990d44828b0b2c11a2ca4d4a'},
                               {'cct/ASCCRevisionNumber': 1},
                               {'cct/ASCCDefinition': 'Identifies the Item associated with the Line or Detail of a transaction. The item provides the details of the generic Item component information.',
                               'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'},
                               {'cct/ASCCPRevisionNumber': 1},
                               {'cct/ASCCPDefinition': 'Specifies details concerning a thing which could be a product, service or virtual things. Item typically provides the details of the generic Item information.',
                               'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                              'xsd/maxOccurs'      :
                              1,
                              'xsd/minOccurs'      :
                              0}]},
                           {'model/elementDef':
                            [{'element/name': 'BuyerParty',
                              'element/complexType':
                              [{'model/sequence':
                                [{'model/elementDef':
                                  [{'element/name': 'TaxIDSet',
                                    'element/complexType':
                                    [{'model/sequence':
                                      [{'model/elementDef':
                                        [{'element/name': 'ID',
                                          'has/documentation':
                                          [{'cct/BCC_GUID': 'oagis-id-59fccca73c224f96a9ae7ea4138c28cf'},
                                           {'cct/BCCP_GUID': 'oagis-id-57f1dbe323d344caa1394f49f923bb89'},
                                           {'cct/BCCRevisionNumber': 2},
                                           {'cct/BCCDefinition': 'a part of the composition identifier, itself an identifier that is only unique in a specific context.'},
                                           {'cct/BCCPRevisionNumber': 1},
                                           {'cct/BCCPDefinition':
                                            'Is the Identifiers of the given instance of an entity within the scope of the integration. The schemeAgencyID attribute identifies the party that provided or knows this party by the given identifier.',
                                            'has/source'        :
                                            'http://www.openapplications.org/oagis/10/platform/2'}],
                                          'xsd/maxOccurs'    :
                                          -1,
                                          'xsd/minOccurs'    :
                                          1,
                                          'xsd/nillable'     :
                                          true,
                                          'xsd/type'         :
                                          'cl_1_US_Tax_IDContentType_af3db2053ebe499ca991bc3a38dc3ae1'}]}],
                                      'has/documentation':
                                      [{'cct/ACC_GUID': 'oagis-id-8537e396814a488087b71496eaec6563'},
                                       {'cct/ACCRevisionNumber': 2},
                                       {'cct/ACCDefinition': 'A set of IDs to build a composite identifier of an object.', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                                    'has/documentation'  :
                                    [{'cct/ASCC_GUID': 'oagis-id-c1bea7eb473a481bb300a1ae5dd2f134'}, {'cct/ASCCP_GUID': 'oagis-id-9ea03290b7c7478bb7409699f73e2c41'}, {'cct/ASCCRevisionNumber': 2}, {'cct/ASCCPRevisionNumber': 1}],
                                    'xsd/maxOccurs'      :
                                    -1,
                                    'xsd/minOccurs'      :
                                    0}]},
                                 {'model/elementDef':
                                  [{'element/name': 'Location',
                                    'element/complexType':
                                    [{'model/sequence':
                                      [{'model/elementDef':
                                        [{'element/name': 'Address',
                                          'element/complexType':
                                          [{'model/sequence':
                                            [{'model/elementDef': [{'element/name': 'BuildingNumber',
                                             'has/documentation': [{'cct/BCC_GUID': 'oagis-id-a09fc2ecaaec4ac080ea509b090fa1c3'},
                                               {'cct/BCCRevisionNumber': 1},
                                               {'cct/BCCDefinition': 'The Number of the building or house on the street that identifies where to deliver mail.  For example, Building 300 on Standards Parkway',
                                               'has/source'       : 'http://www.openapplications.org/oagis/10/platform/2'},
                                               {'cct/BCCP_GUID': 'oagis-id-89a51f0b267146fc853f3e2964be4ef4'},
                                               {'cct/BCCPRevisionNumber': 1},
                                               {'cct/BCCPDefinition': 'The Number of the building or house on the street that identifies where to deliver mail.  For example, Building 300 on Standards Parkway',
                                               'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'}],
                                             'xsd/maxOccurs'    : 1,
                                             'xsd/minOccurs'    : 0,
                                             'xsd/type'         : 'xsd:string'}]},
                                             {'model/elementDef': [{'element/name': 'StreetName',
                                             'has/documentation': [{'cct/BCC_GUID': 'oagis-id-17041d58b9ec4ed7bb5a94a1094e7825'},
                                               {'cct/BCCRevisionNumber': 1},
                                               {'cct/BCCDefinition': 'The Street Name where the building/ house is located', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                               {'cct/BCCP_GUID': 'oagis-id-3a6ee868609c4d4390d145cb42fb585b'},
                                               {'cct/BCCPRevisionNumber': 1},
                                               {'cct/BCCPDefinition': 'The Street Name where the building/ house is located', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                             'xsd/maxOccurs'    : 1,
                                             'xsd/minOccurs'    : 0,
                                             'xsd/type'         : 'xsd:string'}]},
                                             {'model/elementDef': [{'element/name': 'CityName',
                                             'has/documentation': [{'cct/BCC_GUID': 'oagis-id-1dc308b8c3e24a7f8612cb6cbfabac7f'},
                                               {'cct/BCCRevisionNumber': 1},
                                               {'cct/BCCDefinition': 'Identifies the town or the city', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                               {'cct/BCCP_GUID': 'oagis-id-5a8159bc5b4a47529b4c8e6fbe659e7f'},
                                               {'cct/BCCPRevisionNumber': 1},
                                               {'cct/BCCPDefinition': 'Identifies the town or the city', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                             'xsd/maxOccurs'    : 1,
                                             'xsd/minOccurs'    : 0,
                                             'xsd/type'         : 'xsd:string'}]},
                                             {'model/elementDef':
                                              [{'element/name': 'CountryCode',
                                                'has/documentation':
                                                [{'cct/BCC_GUID': 'oagis-id-7e6b51106b6b46979eccf5e683171be8'},
                                                 {'cct/BCCRevisionNumber': 1},
                                                 {'cct/BCCDefinition': 'Country in which the Address is in. A possible list of values is as specified in ISO 3166-2.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                                 {'cct/BCCP_GUID': 'oagis-id-5157fcc44dc54cffa6c0bae7ba1f8912'},
                                                 {'cct/BCCPRevisionNumber': 1},
                                                 {'cct/BCCPDefinition': 'Country in which the Address is in. A possible list of values is as specified in ISO 3166-2.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                                'xsd/maxOccurs'    :
                                                1,
                                                'xsd/minOccurs'    :
                                                0,
                                                'xsd/type'         :
                                                'xsd:token'}]},
                                             {'model/elementDef': [{'element/name': 'PostalCode',
                                             'has/documentation': [{'cct/BCC_GUID': 'oagis-id-7b0478ba4fc040a096a80db845d93f23'},
                                               {'cct/BCCRevisionNumber': 1},
                                               {'cct/BCCDefinition': 'Postal Code of the Address.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'},
                                               {'cct/BCCP_GUID': 'oagis-id-3ec545abfd044edb89ca056208143e37'},
                                               {'cct/BCCPRevisionNumber': 1},
                                               {'cct/BCCPDefinition': 'Postal Code of the Address.', 'has/source': 'http://www.openapplications.org/oagis/10/platform/2'}],
                                             'xsd/maxOccurs'    : 1,
                                             'xsd/minOccurs'    : 0,
                                             'xsd/type'         : 'xsd:normalizedString'}]}],
                                            'has/documentation':
                                            [{'cct/ACC_GUID': 'oagis-id-d3b32b38193c455b984bf9632c8dd78c'}, {'cct/ACCRevisionNumber': 1}]}],
                                          'has/documentation'  :
                                          [{'cct/ASCC_GUID': 'oagis-id-c4cf7383aec7416293a54161d8c61746'}, {'cct/ASCCP_GUID': 'oagis-id-18764ba0412248ae8d2a8e56619fd970'}, {'cct/ASCCRevisionNumber': 1}, {'cct/ASCCPRevisionNumber': 1}],
                                          'xsd/maxOccurs'      :
                                          -1,
                                          'xsd/minOccurs'      :
                                          0}]}],
                                      'has/documentation':
                                      [{'cct/ACC_GUID': 'oagis-id-ac71693f265d4554a6b7e663e4b6ec83'}, {'cct/ACCRevisionNumber': 1}]}],
                                    'has/documentation'  :
                                    [{'cct/ASCC_GUID': 'oagis-id-64a635149fa94f589ea87b8ead8c740f'},
                                     {'cct/ASCCP_GUID': 'oagis-id-5eecda64436b459e85510a774a6dd390'},
                                     {'cct/ASCCRevisionNumber': 2},
                                     {'cct/ASCCDefinition': 'The location of a thing, as captured by an address (or addresses), GPS Coordinates, and/or in relation to other locations.',
                                     'has/source'        : 'http://www.openapplications.org/oagis/10/platform/2'},
                                     {'cct/ASCCPRevisionNumber': 1},
                                     {'cct/ASCCPDefinition': 'The location of a thing, as captured by an address (or addresses), GPS Coordinates, and/or in relation to other locations.',
                                     'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                                    'xsd/maxOccurs'      :
                                    -1,
                                    'xsd/minOccurs'      :
                                    0}]}],
                                'has/documentation':
                                [{'cct/ACC_GUID': 'oagis-id-efa8bce0226244f5b2749b4b4667a19e'}, {'cct/ACCRevisionNumber': 1}]}],
                              'has/documentation'  :
                              [{'cct/ASCC_GUID': 'oagis-id-4e8e475d308048e2b006bd83d56a43d2'},
                               {'cct/ASCCP_GUID': 'oagis-id-7da061d1ecd04284b46ad1bb4d81471c'},
                               {'cct/ASCCRevisionNumber': 1},
                               {'cct/ASCCPRevisionNumber': 1},
                               {'cct/ASCCPDefinition': 'The entity that is responsible for Buying the document or element in which it is associated. Additionally, the contact  may be a person or a group or department.',
                               'has/source'         : 'http://www.openapplications.org/oagis/10/platform/2'}],
                              'xsd/maxOccurs'      :
                              1,
                              'xsd/minOccurs'      :
                              0}]}],
                          'has/documentation':
                          [{'cct/ACC_GUID': 'oagis-id-e8954e7c0d7f4180a00eab149e112c1e'},
                           {'cct/ACCRevisionNumber': 1},
                           {'cct/ACCDefinition': 'The InvoiceLine type supports order line invoicing.', 'has/source': 'http://www.openapplications.org/oagis/10'}]}],
                        'has/documentation'  :
                        [{'cct/ASCC_GUID': 'oagis-id-d565da73a0dc4d93967df30bade554d9'},
                         {'cct/ASCCP_GUID': 'oagis-id-57a00bde1ac9493d9e432ed9caf011e1'},
                         {'cct/ASCCRevisionNumber': 1},
                         {'cct/ASCCDefinition':
                          'In general an Invoice Line can be aligned with Order Lines. The ItemQuantity and Price are used to compute the Total. The Total is a sum of the Total and any Charges, Taxes or Allowances.',
                          'has/source'        :
                          'http://www.openapplications.org/oagis/10'},
                         {'cct/ASCCPRevisionNumber': 1},
                         {'cct/ASCCPDefinition':
                          'In general an Invoice Line can be aligned with Order Lines. The ItemQuantity and Price are used to compute the Total. The Total is a sum of the Total and any Charges, Taxes or Allowances.',
                          'has/source'         :
                          'http://www.openapplications.org/oagis/10'}],
                        'xsd/maxOccurs'      :
                        -1,
                        'xsd/minOccurs'      :
                        0}]}],
                    'has/documentation':
                    [{'cct/ACC_GUID': 'oagis-id-9a47afef92474a61b6ab64247f41a97c'}, {'cct/ACCRevisionNumber': 1}]}],
                  'has/documentation'  :
                  [{'cct/ASCC_GUID': 'oagis-id-9f49996f77c24b2c9fb92d5770a7dc5d'},
                   {'cct/ASCCP_GUID': 'oagis-id-eecc2a8ba86e4a1eac8bf19977a3d9d0'},
                   {'cct/ASCCRevisionNumber': 1},
                   {'cct/ASCCPRevisionNumber': 1},
                   {'cct/ASCCPDefinition': 'The Invoice is used to invoice a customer for goods/services the have been provided.', 'has/source': 'http://www.openapplications.org/oagis/10'}],
                  'xsd/maxOccurs'      :
                  -1,
                  'xsd/minOccurs'      :
                  1}]}],
              'has/documentation':
              [{'cct/ACC_GUID': 'oagis-id-9f8525e4a22944c894bb7943c6de52bb'}, {'cct/ACCRevisionNumber': 1}]}],
            'has/documentation'  :
            [{'cct/ASCC_GUID': 'oagis-id-8859aa7fea054ed2981af5a9be991e6b'},
             {'cct/ASCCP_GUID': 'oagis-id-8859aa7fea054ed2981af5a9be991e6b'},
             {'cct/ASCCRevisionNumber': 1},
             {'cct/ASCCDefinition':
              'Is where the information that the BOD message carries is provided, in this case ProcessInvoice. The information consists of a Verb and one or more Nouns. The verb (Process) indicates the action to be performed on the Noun (Invoice).',
              'has/source'        :
              'http://www.openapplications.org/oagis/10'},
             {'cct/ASCCPRevisionNumber': 1},
             {'cct/ASCCPDefinition':
              'Is where the information that the BOD message carries is provided, in this case ProcessInvoice. The information consists of a Verb and one or more Nouns. The verb (Process) indicates the action to be performed on the Noun (Invoice).',
              'has/source'         :
              'http://www.openapplications.org/oagis/10'}],
            'xsd/maxOccurs'      :
            1,
            'xsd/minOccurs'      :
            1}]}],
        'has/documentation':
        [{'cct/ACC_GUID': 'oagis-id-b51a29bbe9c94ae986d44ddcd9d7f627'}, {'cct/ACCRevisionNumber': 1}]}],
      'has/documentation'  :
      [{'cct/ASCCP_GUID': 'oagis-id-7814973e3d19488ba917cb61ac28b257'},
       {'cct/BusinessContext': [{'cct/GUID': 'oagis-id-4e7c8ef8fc9b49878b0cedfe997a5320', 'cct/Name': 'BC_2'}]},
       {'cct/ASCCPRevisionNumber': 1},
       {'cct/ASCCPDefinition': 'The purpose of the ProcessInvoice is to transmit an invoice from a supplier to a customer. Indicating that the receiver of the Invoice is to Process the Invoice for payment.',
       'has/source'         : 'http://www.openapplications.org/oagis/10'}]}]},
   {'model/simpleType': [{'model/restriction': [{'restriction/attributes': [{'xsd/base': ['xsd:normalizedString']}]}]}]}],
  'schema/attributes':
  [{'xsd/attributeFormDefault': 'unqualified', 'xsd/elementFormDefault': 'qualified', 'xsd/targetNamespace': 'http://www.openapplications.org/oagis/10'}]};")
