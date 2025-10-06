# Configuration file for the Sphinx documentation builder.
#
# For the full list of built-in configuration values, see the documentation:
# https://www.sphinx-doc.org/en/master/usage/configuration.html

# -- Project information -----------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#project-information

project = 'CauMon'
copyright = '2025, Zhenya Zhang'
author = 'Zhenya Zhang'
release = '1.0'

# -- General configuration ---------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#general-configuration

extensions = [
    'myst_parser',     # 支持 Markdown
    'breathe',         # 支持 Doxygen XML
]

templates_path = ['_templates']
exclude_patterns = []



# -- Options for HTML output -------------------------------------------------
# https://www.sphinx-doc.org/en/master/usage/configuration.html#options-for-html-output

html_theme = 'sphinx_rtd_theme'

breathe_projects = {
    "CauMon": "../doxygen/xml"
}
breathe_default_project = "CauMon"


import os
import sys
sys.path.insert(0, os.path.abspath('../..'))
