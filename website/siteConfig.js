/**
 * Copyright (c) 2017-present, Facebook, Inc.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

// See https://docusaurus.io/docs/site-config for all the possible
// site configuration options.

// List of projects/orgs using your project for the users page.
const users = [
  {
    caption: 'User1',
    // You will need to prepend the image path with your baseUrl
    // if it is not '/', like: '/test-site/img/image.jpg'.
    image: '/img/undraw_open_source.svg',
    infoLink: 'https://www.facebook.com',
    pinned: true,
  },
];

const siteConfig = {
  title: 'Scanamo',
  tagline: 'Simpler DynamoDB access for Scala',
  url: 'https://www.scanamo.org',
  baseUrl: '/',

  projectName: 'scanamo',
  organizationName: 'org.scanamo',

  // For no header links in the top nav bar -> headerLinks: [],
  headerLinks: [
    { doc: 'operations', label: 'Operations ' },
    { doc: 'batch-operations', label: 'Batch Operations ' },
    { doc: 'conditional-operations', label: 'Conditional Operations ' },
    { doc: 'filters', label: 'Filters ' },
    { doc: 'using-indexes', label: 'Using Indexes ' },
    { doc: 'dynamo-format', label: 'DynamoFormat ' },
    { doc: 'asynchronous', label: 'Asynchronous Requests' },
  ],

  // If you have users set above, you add it here:
  users,

  /* path to images for header/footer */
  headerIcon: 'img/favicon.ico',
  footerIcon: 'img/favicon.ico',
  favicon: 'img/favicon.ico',

  /* Colors for website */
  colors: {
    primaryColor: '#3e4069',
    secondaryColor: '#2b2c49',
  },

  /* Custom fonts for website */
  /*
  fonts: {
    myFont: [
      "Times New Roman",
      "Serif"
    ],
    myOtherFont: [
      "-apple-system",
      "system-ui"
    ]
  },
  */

  // This copyright info is used in /core/Footer.js and blog RSS/Atom feeds.
  copyright: `Copyright © ${new Date().getFullYear()} Scanamo Contributors`,

  highlight: {
    // Highlight.js theme to use for syntax highlighting in code blocks.
    theme: 'default',
  },

  // Add custom scripts here that would be placed in <script> tags.
  scripts: [
    // 'https://buttons.github.io/buttons.js'
  ],

  // On page navigation for the current documentation page.
  onPageNav: 'separate',
  // No .html extensions for paths.
  cleanUrl: true,

  // Open Graph and Twitter card images.
  // ogImage: 'img/undraw_online.svg',
  // twitterImage: 'img/undraw_tweetstorm.svg',

  // Show documentation's last contributor's name.
  enableUpdateBy: true,

  // Show documentation's last update time.
  enableUpdateTime: true,

  // You may provide arbitrary config keys to be used as needed by your
  // template. For example, if you need your repo's URL...
  //   repoUrl: 'https://github.com/facebook/test-site',

  customDocsPath: 'scanamo-docs/target/mdoc',
};

module.exports = siteConfig;
