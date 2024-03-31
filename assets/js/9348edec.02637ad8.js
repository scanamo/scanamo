"use strict";(self.webpackChunkscanamo_website=self.webpackChunkscanamo_website||[]).push([[660],{4857:(e,n,t)=>{t.r(n),t.d(n,{assets:()=>l,contentTitle:()=>r,default:()=>m,frontMatter:()=>i,metadata:()=>s,toc:()=>c});var o=t(4848),a=t(8453);const i={title:"Filters",sidebar_position:4},r=void 0,s={id:"filters",title:"Filters",description:"Scans and Queries can be filtered within Dynamo, preventing the memory, network and marshalling overhead of filtering on the client.",source:"@site/docs/filters.md",sourceDirName:".",slug:"/filters",permalink:"/filters",draft:!1,unlisted:!1,editUrl:"https://github.com/scanamo/scanamo/blob/main/docs/filters.md",tags:[],version:"current",sidebarPosition:4,frontMatter:{title:"Filters",sidebar_position:4},sidebar:"tutorialSidebar",previous:{title:"Conditional Operations",permalink:"/conditional-operations"},next:{title:"Using Indexes",permalink:"/using-indexes"}},l={},c=[];function d(e){const n={a:"a",code:"code",em:"em",p:"p",pre:"pre",...(0,a.R)(),...e.components};return(0,o.jsxs)(o.Fragment,{children:[(0,o.jsxs)(n.p,{children:[(0,o.jsx)(n.a,{href:"/operations#scan",children:"Scans"})," and ",(0,o.jsx)(n.a,{href:"/operations#query",children:"Queries"})," can be filtered within Dynamo, preventing the memory, network and marshalling overhead of filtering on the client."]}),"\n",(0,o.jsxs)(n.p,{children:["Note that these filters do ",(0,o.jsx)(n.em,{children:"not"})," reduce the ",(0,o.jsx)(n.a,{href:"http://docs.aws.amazon.com/amazondynamodb/latest/developerguide/HowItWorks.ProvisionedThroughput.html",children:"consumed capacity"})," in Dynamo. Even though a filter may lead to a small number of results being\nreturned, it could still exhaust the provisioned capacity or force the provisioned capacity to autoscale up to an expensive level."]}),"\n",(0,o.jsx)(n.pre,{children:(0,o.jsx)(n.code,{className:"language-scala",children:'import org.scanamo._\nimport org.scanamo.syntax._\nimport org.scanamo.generic.auto._\nimport software.amazon.awssdk.services.dynamodb.model.ScalarAttributeType._\nval client = LocalDynamoDB.syncClient()\nval scanamo = Scanamo(client)\n\ncase class Station(line: String, name: String, zone: Int)\nval stationTable = Table[Station]("Station")\n'})}),"\n",(0,o.jsx)(n.pre,{children:(0,o.jsx)(n.code,{className:"language-scala",children:'LocalDynamoDB.withTable(client)("Station")("line" -> S, "name" -> S) {\n  val ops = for {\n    _ <- stationTable.putAll(Set(\n      Station("Metropolitan", "Chalfont & Latimer", 8),\n      Station("Metropolitan", "Chorleywood", 7),\n      Station("Metropolitan", "Rickmansworth", 7),\n      Station("Metropolitan", "Croxley", 7),\n      Station("Jubilee", "Canons Park", 5)\n    ))\n    filteredStations <-\n      stationTable\n        .filter("zone" < 8)\n        .query("line" === "Metropolitan" and ("name" beginsWith "C"))\n  } yield filteredStations\n  scanamo.exec(ops)\n}\n// res0: List[Either[DynamoReadError, Station]] = List(\n//   Right(Station("Metropolitan", "Chorleywood", 7)),\n//   Right(Station("Metropolitan", "Croxley", 7))\n// )\n'})}),"\n",(0,o.jsxs)(n.p,{children:["More examples can be found in the ",(0,o.jsx)(n.code,{children:"Table"})," scaladoc."]})]})}function m(e={}){const{wrapper:n}={...(0,a.R)(),...e.components};return n?(0,o.jsx)(n,{...e,children:(0,o.jsx)(d,{...e})}):d(e)}},8453:(e,n,t)=>{t.d(n,{R:()=>r,x:()=>s});var o=t(6540);const a={},i=o.createContext(a);function r(e){const n=o.useContext(i);return o.useMemo((function(){return"function"==typeof e?e(n):{...n,...e}}),[n,e])}function s(e){let n;return n=e.disableParentContext?"function"==typeof e.components?e.components(a):e.components||a:r(e.components),o.createElement(i.Provider,{value:n},e.children)}}}]);