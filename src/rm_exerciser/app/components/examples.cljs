(ns rm-exerciser.app.components.examples)

(declare rm-examples)

(defn get-example [name]
  (some #(when (= name (:name %)) %) rm-examples))


(def rm-examples
  [{:name "Schema from server"
    :code "$read([['schema/name', 'urn:oagis-10.8:Nouns:Invoice'],
       ['schema-object']])"}

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

  $reduce($bSet, $eFn) )"
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
  [$aRes, $bRes] )"
    :data ""}


;;;======================= Elena-1
   {:name "Elena 1"
    :code
"( $q := query{[?e :schema_complexTypes ?c]
              [?e :sp_name ?superName]
              [?c :model_sequence ?m]
              [?m :sp_name ?subName]};
  $q([$schemaA, $schemaB]) )"
#_"[$schemaA, $schemaB].library_content.
schema_complexTypes.
model_sequence.
sp_name"
    :data
"
   $schemaA := {'schema_subversion':'',
                'library_content':
                [{'sp_name':'Invoice',
                  'schema_complexTypes':
                  {'model_sequence':
                   [{'sp_name':'Invoice_ID',
                     'sp_type':'xs:int',
                     'sp_minOccurs':1,
                     'sp_maxOccurs':2,
                     'sp_function':{'fn_type':'gelem'}},
                    {'sp_name':'Document_date',
                     'sp_type':'US_Date',
                     'sp_minOccurs':1,
                     'sp_maxOccurs':1,
                     'sp_function':{'fn_type':'gelem'}},
                    {'sp_name':'Address',
                     'sp_minOccurs':1,
                     'sp_maxOccurs':1,
                     'sp_function':{'fn_type':'gelem'}}]},
                  'sp_function':{'fn_type':'typeRef'}},
                 {'sp_name':'Address',
                  'schema_complexTypes':
                  {'model_sequence':
                   [{'sp_name':'Address_line_1',
                     'sp_type':'xs:string',
                     'sp_minOccurs':1,
                     'sp_maxOccurs':1,
                     'sp_function':{'fn_type':'gelem'}},
                    {'sp_name':'Address_line_2',
                     'sp_type':'xs:string',
                     'sp_minOccurs':1,
                     'sp_maxOccurs':1,
                     'sp_function':{'fn_type':'gelem'}}]},
                  'sp_function':{'fn_type':'typeRef'}}],
                'schema_pathname':'data/testing/elena/Company A - Invoice_xsd',
                'schema_sdo':'unknown',
                'schema_type':'generic_xsd-file',
                'schema_name':'Company A - Invoice_xsd',
                'schema_spec':'default',
                'schema_version':''};


   $schemaB :=  {'schema_subversion':'',
                 'library_content':
                 [{'sp_name':'Data',
                   'schema_complexTypes':
                   {'model_sequence':
                    [{'sp_name':'Invoice',
                      'schema_complexTypes':
                      {'model_sequence':
                       [{'sp_name':'Invoice_ID',
                         'sp_type':'xs:int',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'},
                         'doc_doc-string':
                         'This elemet is used to define the ID of an invoice document'},
                        {'sp_name':'Creation_date',
                         'sp_type':'EUDateFormat',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'Bill_to_address',
                         'sp_type':'xs:int',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}}]},
                      'sp_function':{'fn_type':'gelem'}},
                     {'sp_name':'Address',
                      'schema_complexTypes':
                      {'model_sequence':
                       [{'sp_name':'Address_ID',
                         'sp_type':'xs:int',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'Street_number',
                         'sp_type':'xs:int',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'Street_name',
                         'sp_type':'xs:string',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'City',
                         'sp_type':'xs:string',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'State',
                         'sp_type':'State_list',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}},
                        {'sp_name':'Zip_code',
                         'sp_type':'xs:string',
                         'sp_minOccurs':1,
                         'sp_maxOccurs':1,
                         'sp_function':{'fn_type':'gelem'}}]},
                      'sp_function':{'fn_type':'gelem'}}]},
                   'sp_function':{'fn_type':'typeRef'}}],
                 'schema_pathname':'data/testing/elena/Company B - Invoice_xsd',
                'schema_sdo':'unknown',
                'schema_type':'generic_xsd-file',
                'schema_name':'Company B - Invoice_xsd',
               'schema_spec':'default',
               'schema_version':''}"}])
